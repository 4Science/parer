package it.ivscience.parer.it;

import it.ivscience.parer.MainApplication;
import it.ivscience.parer.content.service.SipFileService;
import it.ivscience.parer.content.service.SipPackageService;
import it.ivscience.parer.worker.BatchRunner;
import it.ivscience.parer.worker.SipPipeline;
import it.ivscience.parer.worker.ftp.FtpClient;
import it.ivscience.parer.worker.step.ZipToFtpStep;
import it.ivscience.parer.worker.parer.NotificationClient;
import it.ivscience.parer.worker.parer.PreIngestClient;
import it.ivscience.parer.worker.s3.S3Service;
import it.ivscience.parer.worker.s3.impl.S3ServiceImpl;
import it.ivscience.parer.worker.sip.SipBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * End-to-end integration test with LocalStack S3.
 *
 * Uses a real S3Client and S3ServiceImpl (streaming), verifying that the
 * pipeline correctly reads from a real S3-compatible service via CLI-driven
 * BatchRunner invocation.
 */
@SpringBootTest(classes = MainApplication.class, properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "aws.region=us-east-1",
        "aws.accessKeyId=test",
        "aws.secretAccessKey=test"
})
@ActiveProfiles("test")
@Testcontainers
@Import(LocalSipPipelineIT.Config.class)
class LocalSipPipelineIT {

    static {
        System.setProperty("aws.region", "us-east-1");
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    private static final String BUCKET = "localstack-test-bucket";
    private static final String TEST_SIP_ID = "IT-LOCALSTACK-FIXED-ID";
    private static final String PROCESS_KEY = "parer/esecuzioni/test/2026/01/" + TEST_SIP_ID + ".process";
    private static final String CONTENT_KEY  = "objects/" + TEST_SIP_ID;

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.S3);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("parer.aws.access-key", localstack::getAccessKey);
        registry.add("parer.aws.secret-key", localstack::getSecretKey);
        registry.add("parer.aws.region", localstack::getRegion);
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
    }

    @TestConfiguration
    static class Config {
        @Bean("localStackS3Client")
        @Primary
        public S3Client s3Client() {
            return S3Client.builder()
                    .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                    .region(Region.of(localstack.getRegion()))
                    .build();
        }

        @Bean("localStackS3Service")
        @Primary
        public S3Service s3Service(S3Client s3Client) {
            return new S3ServiceImpl();
        }
    }

    @Autowired
    protected S3Client s3Client;

    @MockBean
    protected SecretsManagerClient secretsManagerClient;

    @SpyBean
    protected PreIngestClient preIngestClient;

    @SpyBean
    protected NotificationClient notificationClient;

    @SpyBean
    protected FtpClient ftpClient;

    @SpyBean
    protected SipBuilder sipBuilder;

    @Autowired
    protected SipPipeline sipPipeline;

    @Autowired
    protected ZipToFtpStep fileTransferStep;

    @Autowired
    protected BatchRunner batchRunner;

    @Autowired
    protected SipPackageService sipPackageService;

    @Autowired
    protected SipFileService sipFileService;

    @BeforeEach
    void resetSpies() {
        Mockito.reset(preIngestClient, notificationClient, ftpClient, sipBuilder);
        // Use single-file mode: S3ZipperClient behavior is mocked via S3Service and won't create a real ZIP in LocalStack S3
        fileTransferStep.setSendSingleFiles(true);
    }

    @BeforeAll
    static void setupS3() {
        S3Client s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();

        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        // Upload real tiff files as content (flat structure — no principale/ subdir)
        Path base = Paths.get("principale");
        String[] files = {
                "80992-001-piatto anteriore.tiff",
                "80992-002-controguardia anteriore.tiff"
        };
        for (String f : files) {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(BUCKET)
                            .key(CONTENT_KEY + "/" + f)
                            .build(),
                    base.resolve(f)
            );
        }

        // Upload .process descriptor
        String descriptor = String.format(
                "{\"object_id\":\"%s\",\"bucket\":\"%s\",\"key\":\"%s\"}",
                TEST_SIP_ID, BUCKET, CONTENT_KEY);
        s3.putObject(
                PutObjectRequest.builder().bucket(BUCKET).key(PROCESS_KEY).build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(
                        descriptor.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void pipelineProcessesFilesFromLocalStackS3() {
        int exit = batchRunner.runBatch(new DefaultApplicationArguments(
                "--s3-bucket=" + BUCKET,
                "--s3-key=" + PROCESS_KEY));

        assertThat(exit).isEqualTo(0);

        var pkg = sipPackageService.findBySipId(TEST_SIP_ID);
        assertThat(pkg).isPresent();
        assertThat(pkg.get().getStatus()).isEqualTo("SUCCESS");

        var files = sipFileService.findBySipPackage(pkg.get());
        assertThat(files).isNotEmpty();
        assertThat(files.get(0).getChecksum()).isNotEmpty();

        verify(preIngestClient).sendPreIngest(any());
        verify(notificationClient).notifyFileTransfer(any());
    }
}
