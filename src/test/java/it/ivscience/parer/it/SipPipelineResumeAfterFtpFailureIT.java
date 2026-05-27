package it.ivscience.parer.it;

import it.ivscience.parer.worker.errors.TransientException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SipPipelineResumeAfterFtpFailureIT extends AbstractPipelineIT {

    @Test
    void resumesFromFtpStepWithoutRepeatingPreIngest() {
        String sipId      = "IT-RESUME-" + UUID.randomUUID();
        String processKey = processKeyFor(sipId);

        // Run 1: FTP fails
        doThrow(new TransientException("FTP server down"))
                .when(ftpClient).upload(anyString(), anyString(), any(InputStream.class), anyLong());
        int exit1 = runBatch("test-bucket", processKey);
        assertThat(exit1).isEqualTo(1);
        assertThat(sipPackageService.findBySipId(sipId).get().getStatus())
                .isEqualTo("FAILED_FTP_TRANSFER");

        // Run 2: FTP restored
        org.mockito.Mockito.reset(ftpClient, preIngestClient, notificationClient, sipBuilder);
        setupDefaultMockBehavior();
        int exit2 = runBatch("test-bucket", processKey);
        assertThat(exit2).isEqualTo(0);
        assertThat(sipPackageService.findBySipId(sipId).get().getStatus()).isEqualTo("SUCCESS");
        verify(preIngestClient, never()).sendPreIngest(any());
        verify(notificationClient).notifyFileTransfer(any());
    }
}
