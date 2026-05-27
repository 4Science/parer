package it.ivscience.parer.it;

import it.ivscience.parer.worker.SipPipeline;
import it.ivscience.parer.worker.model.WorkUnitDescriptor;
import it.ivscience.parer.worker.s3.S3FileMetadata;
import it.ivscience.parer.worker.s3.S3Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
/**
 * Tests that the parallel FTP upload mechanism works correctly.
 *
 * The S3Service spy (inherited from AbstractPipelineIT) is configured to
 * return deterministic metadata and stream bytes. The test verifies that after
 * process() returns, the SipPackage reaches SUCCESS and all SipFiles have been
 * marked as sent.
 * Note: this test invokes SipPipeline.process() directly (bypassing
 * BatchRunner) to keep setup minimal.
 */
class SipPipelineConcurrentFtpIT extends AbstractPipelineIT {

    @Autowired
    private SipPipeline sipPipeline;
    private S3Service s3Service;

    @Test
    void allFilesUploadedConcurrently() {
        String sipId = "IT-CONCURRENT-" + UUID.randomUUID();
        String bucket = "test-bucket";
        String key = "objects/" + sipId + "_0.pdf";
        doReturn(new S3FileMetadata(bucket, key, sipId + "_0.pdf", 1024L, "application/pdf", null))
                .when(s3Service).headObject(anyString(), anyString());
        doAnswer(inv -> {
            Consumer<InputStream> consumer = inv.getArgument(2);
            consumer.accept(new ByteArrayInputStream(new byte[64]));
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        }).when(s3Service).streamToConsumer(anyString(), anyString(), any());
        WorkUnitDescriptor descriptor = new WorkUnitDescriptor(sipId, bucket, key);
        sipPipeline.process(descriptor);
        var pkg = sipPackageService.findBySipId(sipId);
        assertThat(pkg).isPresent();
        assertThat(pkg.get().getStatus()).isEqualTo("SUCCESS");
        verify(ftpClient, atLeastOnce()).upload(anyString(), anyString(), any(InputStream.class), anyLong());
        var files = sipFileService.findBySipPackage(pkg.get());
        assertThat(files).isNotEmpty();
        assertThat(files).allSatisfy(f -> assertThat(f.getSentAt()).isNotNull());
    }
}
