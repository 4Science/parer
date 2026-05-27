package it.ivscience.parer.it;

import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.s3.S3Service;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for the ZIP transfer mode (default, no -Dftptransfer-sendsinglefiles).
 */
class SipPipelineZipIT extends AbstractPipelineIT {

    @Autowired
    private S3Service s3Service;

    @Test
    void zipModeReachesSuccess() {
        String sipId = "IT-ZIP-" + UUID.randomUUID();
        String processKey = processKeyFor(sipId);

        int exit = runBatch("test-bucket", processKey);

        assertThat(exit).isEqualTo(0);

        var pkg = sipPackageService.findBySipId(sipId);
        assertThat(pkg).isPresent();
        assertThat(pkg.get().getStatus()).isEqualTo("SUCCESS");

        var files = sipFileService.findBySipPackage(pkg.get());
        assertThat(files).isNotEmpty();
        // All files marked sent with the ZIP filename
        files.forEach(f -> {
            assertThat(f.getSentAt()).isNotNull();
            assertThat(f.getRemoteFileName()).endsWith(".zip");
        });

        verify(s3Service).createZip(anyString(), any(List.class), anyString(), anyString(), anyString());
        verify(ftpClient).upload(anyString(), anyString(), any(), Mockito.anyLong());
        verify(notificationClient).notifyFileTransfer(any());
    }

    @Test
    void zipModeIdempotentOnResume() {
        String sipId = "IT-ZIP-IDEMPOTENT-" + UUID.randomUUID();
        String processKey = processKeyFor(sipId);

        runBatch("test-bucket", processKey);
        assertThat(sipPackageService.findBySipId(sipId).get().getStatus())
                .isEqualTo("SUCCESS");

        Mockito.clearInvocations(s3Service, ftpClient, notificationClient, preIngestClient);

        // Second run: idempotency by SUCCESS status check — pipeline exits before STEP 2
        runBatch("test-bucket", processKey);

        verify(s3Service, Mockito.never()).createZip(any(), any(), any(), any(), any());
        verify(preIngestClient, Mockito.never()).sendPreIngest(any());
    }

    @Test
    void zipModeS3ZipperFailureSetsFtpTransferFailed() {
        String sipId = "IT-ZIP-FAIL-" + UUID.randomUUID();
        String processKey = processKeyFor(sipId);

        Mockito.doThrow(new TransientException("S3zipper unavailable"))
                .when(s3Service).createZip(any(), any(), any(), any(), any());

        int exit = runBatch("test-bucket", processKey);

        assertThat(exit).isNotEqualTo(0);
        assertThat(sipPackageService.findBySipId(sipId).get().getStatus())
                .isEqualTo("FAILED_FTP_TRANSFER");
    }
}
