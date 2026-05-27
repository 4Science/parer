package it.ivscience.parer.it;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.worker.s3.S3Service;
import it.ivscience.parer.worker.step.ZipToFtpStep;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.s3.S3FileMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies per-file resume granularity after a partial FTP failure.
 *
 * Scenario: 3 files, all FTP calls fail in run 1.
 * Between runs, file1 is manually marked as already transferred
 * (simulating it having completed before the crash).
 * Run 2 must upload only file2 and file3, skip sendPreIngest,
 * and reach SUCCESS.
 */
class SipPipelinePartialFtpResumeIT extends AbstractPipelineIT {

    @Autowired
    private ZipToFtpStep fileTransferStep;
    @Autowired
    private S3Service s3Service;

    @BeforeEach
    void enableSingleFileMode() {
        fileTransferStep.setSendSingleFiles(true);
    }

    @AfterEach
    void restoreZipMode() {
        fileTransferStep.setSendSingleFiles(false);
    }

    private static final String MOCK_CHECKSUM =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void resumesOnlyUnsentFilesAfterPartialTransfer() {
        String sipId   = "IT-PARTIAL-" + UUID.randomUUID();
        String flatDir = sipKeyFor(sipId) + "/";

        // Flat structure: all content files in the same directory
        S3FileMetadata file1 = meta("test-bucket", flatDir, "file1.pdf");
        S3FileMetadata file2 = meta("test-bucket", flatDir, "file2.pdf");
        S3FileMetadata file3 = meta("test-bucket", flatDir, "file3.pdf");

        doReturn(List.of(file1, file2, file3)).when(s3Service).listObjects("test-bucket", flatDir);

        String processKey = processKeyFor(sipId);

        // ── Run 1: FTP fails for all files ──────────────────────────────────
        doThrow(new TransientException("FTP server down"))
                .when(ftpClient).upload(anyString(), anyString(), any(InputStream.class), anyLong());

        assertThat(runBatch("test-bucket", processKey)).isEqualTo(1);
        assertThat(sipPackageService.findBySipId(sipId).get().getStatus())
                .isEqualTo("FAILED_FTP_TRANSFER");

        // ── Simulate file1 having been transferred before the crash ─────────
        SipPackage pkg = sipPackageService.findBySipId(sipId).get();
        SipFile sipFile1 = sipFileService.findBySipPackage(pkg).stream()
                .filter(f -> "file1.pdf".equals(f.getFileName()))
                .findFirst()
                .orElseThrow();
        sipFileService.markSent(sipFile1, OffsetDateTime.now(), "file1.pdf", MOCK_CHECKSUM);

        // ── Run 2: FTP restored (SipFiles already in DB — listObjects not called again) ──
        reset(ftpClient, preIngestClient, notificationClient, sipBuilder);

        assertThat(runBatch("test-bucket", processKey)).isEqualTo(0);

        assertThat(sipPackageService.findBySipId(sipId).get().getStatus())
                .isEqualTo("SUCCESS");

        // file1 already sent → file2, file3, and SIP XML re-uploaded (objectCode = parerKey, not sipId)
        verify(ftpClient, times(3)).upload(anyString(), anyString(), any(InputStream.class), anyLong());

        // STEP 1 must not repeat
        verify(preIngestClient, never()).sendPreIngest(any());

        // Notifica includes all 3 content files + SIP XML
        verify(notificationClient).notifyFileTransfer(
                argThat(req -> req.getDepositedFiles().size() == 4));
    }

    private static S3FileMetadata meta(String bucket, String prefix, String fileName) {
        String key = prefix.endsWith("/") ? prefix + fileName : prefix + "/" + fileName;
        return new S3FileMetadata(bucket, key, fileName, 0L, "application/pdf", MOCK_CHECKSUM);
    }

    private static <T> T argThat(java.util.function.Predicate<T> pred) {
        return org.mockito.ArgumentMatchers.argThat(pred::test);
    }
}
