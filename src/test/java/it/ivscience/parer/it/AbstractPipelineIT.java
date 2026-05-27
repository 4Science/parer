package it.ivscience.parer.it;

import it.ivscience.parer.MainApplication;
import it.ivscience.parer.content.service.SipFileService;
import it.ivscience.parer.content.service.SipPackageService;
import it.ivscience.parer.worker.BatchRunner;
import it.ivscience.parer.worker.ftp.FtpClient;
import it.ivscience.parer.worker.metadata.MetadataResolver;
import it.ivscience.parer.worker.parer.NotificationClient;
import it.ivscience.parer.worker.parer.PreIngestClient;
import it.ivscience.parer.worker.parer.model.NotificationResponse;
import it.ivscience.parer.worker.parer.model.PreIngestResponse;
import it.ivscience.parer.worker.s3.S3FileMetadata;
import it.ivscience.parer.worker.s3.S3Service;
import it.ivscience.parer.worker.sip.SipBuilder;
import it.ivscience.parer.worker.sip.SipIndexContext;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Base class for all integration tests of the SIP ingestion pipeline.
 *
 * Spring context is shared among all subclasses (@SpringBootTest with no
 * @DirtiesContext) to keep startup time low. Each test uses a unique sipId
 * so DB rows never collide. SpyBeans are reset in @BeforeEach so that stubbing
 * from one test does not leak into the next.
 *
 * Tests invoke batchRunner.runBatch(cliArgs) directly. The .process trigger file
 * is empty — the SIP key and objectId are derived from --s3-key by BatchRunner.
 * Use processKeyFor(sipId) to build the key and sipKeyFor(sipId) for the S3 dir prefix.
 */
@SpringBootTest(classes = MainApplication.class)
@ActiveProfiles("test")
public abstract class AbstractPipelineIT {

    private static final String MOCK_CHECKSUM =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** Replaces the real AWS S3Client bean. */
    @MockBean
    protected S3Client s3Client;

    /** Replaces the real AWS SecretsManagerClient bean. */
    @MockBean
    protected SecretsManagerClient secretsManagerClient;

    /** Mocks ParER pre-ingest calls — default returns OK. */
    @MockBean
    protected PreIngestClient preIngestClient;

    /** Mocks ParER notification calls — default returns OK. */
    @MockBean
    protected NotificationClient notificationClient;

    /** Mocks FTP/SFTP upload — default no-op. */
    @MockBean
    protected FtpClient ftpClient;

    /** Mocks artifact writes to S3 — default no-op. */
    @MockBean
    protected S3Service s3Service;

    /** Mocks DSpace metadata resolution — default returns empty context. */
    @MockBean
    protected MetadataResolver metadataResolver;

    /** Spy on SipBuilder — allows injecting invalid XML for XSD-failure tests. */
    @SpyBean
    protected SipBuilder sipBuilder;

    @Autowired
    protected BatchRunner batchRunner;

    @Autowired
    protected SipPackageService sipPackageService;

    @Autowired
    protected SipFileService sipFileService;

    @BeforeEach
    void resetSpies() {
        Mockito.reset(preIngestClient, notificationClient, ftpClient, s3Service,
                      metadataResolver, sipBuilder);
        setupDefaultMockBehavior();
    }

    protected void setupDefaultMockBehavior() {
        when(preIngestClient.sendPreIngest(any())).thenReturn(preIngestOk());
        when(notificationClient.notifyFileTransfer(any())).thenReturn(notificaOk());
        when(metadataResolver.resolve(any())).thenReturn(new SipIndexContext());

        S3FileMetadata mockMeta = new S3FileMetadata("test-bucket", "prefix/document_principale.pdf",
                "document_principale.pdf", 1024L, "application/pdf", MOCK_CHECKSUM);
        when(s3Service.listObjects(any(), any())).thenReturn(List.of(mockMeta));
        when(s3Service.headObject(any(), any())).thenReturn(
                new S3FileMetadata("test-bucket", "prefix/file", "file", 0L,
                        "application/octet-stream", MOCK_CHECKSUM));
        when(s3Service.readString(any(), any())).thenReturn("{}");
        when(s3Service.streamToConsumer(any(), any(), any())).thenAnswer(inv -> {
            java.util.function.Consumer<java.io.InputStream> consumer = inv.getArgument(2);
            consumer.accept(new ByteArrayInputStream(new byte[0]));
            return MOCK_CHECKSUM;
        });
    }

    // ── CLI helpers ───────────────────────────────────────────────────────────

    /** S3 key of the .process trigger for the given sipId. */
    protected String processKeyFor(String sipId) {
        return "parer/esecuzioni/test/" + sipId + ".process";
    }

    /** S3 directory prefix (without trailing slash) where SIP content files live. */
    protected String sipKeyFor(String sipId) {
        return "test/" + sipId;
    }

    protected int runBatch(String bucket, String processKey) {
        return batchRunner.runBatch(new DefaultApplicationArguments(
                "--s3-bucket=" + bucket,
                "--s3-key=" + processKey));
    }

    // ── Client response helpers ──────────────────────────────────────────────

    protected PreIngestResponse preIngestOk() {
        PreIngestResponse r = new PreIngestResponse();
        r.setOutcome("OK");
        return r;
    }

    protected PreIngestResponse preIngestKo(String code, String description) {
        PreIngestResponse r = new PreIngestResponse();
        r.setOutcome("KO");
        r.setErrorCode(code);
        r.setErrorMessage(description);
        return r;
    }

    protected NotificationResponse notificaOk() {
        NotificationResponse r = new NotificationResponse();
        r.setOutcome("OK");
        return r;
    }
}
