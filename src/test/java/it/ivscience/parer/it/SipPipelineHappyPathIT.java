package it.ivscience.parer.it;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class SipPipelineHappyPathIT extends AbstractPipelineIT {

    @Test
    void fullPipelineReachesSuccess() {
        String sipId       = "IT-HAPPY-" + UUID.randomUUID();
        String processKey  = processKeyFor(sipId);

        int exit = runBatch("test-bucket", processKey);

        assertThat(exit).isEqualTo(0);
        var pkg = sipPackageService.findBySipId(sipId);
        assertThat(pkg).isPresent();
        assertThat(pkg.get().getStatus()).isEqualTo("SUCCESS");
        assertThat(pkg.get().getSubmittedAt()).isNotNull();
        var files = sipFileService.findBySipPackage(pkg.get());
        assertThat(files).isNotEmpty();
        assertThat(files.get(0).getSentAt()).isNotNull();
        assertThat(files.get(0).getChecksum()).isNotBlank();
        verify(preIngestClient).sendPreIngest(any());
        verify(notificationClient).notifyFileTransfer(any());
    }

    @Test
    void idempotentSkipWhenAlreadySuccess() {
        String sipId      = "IT-IDEMPOTENT-" + UUID.randomUUID();
        String processKey = processKeyFor(sipId);

        // First run → SUCCESS
        runBatch("test-bucket", processKey);
        assertThat(sipPackageService.findBySipId(sipId).get().getStatus()).isEqualTo("SUCCESS");

        // Reset invocations and run again
        org.mockito.Mockito.clearInvocations(preIngestClient, notificationClient, ftpClient);
        runBatch("test-bucket", processKey);

        // Pipeline should skip entirely — no ParER calls
        verify(preIngestClient, org.mockito.Mockito.never()).sendPreIngest(any());
    }
}
