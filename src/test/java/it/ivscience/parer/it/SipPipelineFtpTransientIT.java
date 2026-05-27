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

class SipPipelineFtpTransientIT extends AbstractPipelineIT {

    @Test
    void ftpErrorSetsFailedStatusAndReturnsExitOne() {
        String sipId      = "IT-FTP-TRANSIENT-" + UUID.randomUUID();
        String processKey = processKeyFor(sipId);

        doThrow(new TransientException("Connection refused — FTP server unreachable"))
                .when(ftpClient).upload(anyString(), anyString(), any(InputStream.class), anyLong());

        int exit = runBatch("test-bucket", processKey);

        assertThat(exit).isEqualTo(1);
        var pkg = sipPackageService.findBySipId(sipId);
        assertThat(pkg).isPresent();
        assertThat(pkg.get().getStatus()).isEqualTo("FAILED_FTP_TRANSFER");
        verify(notificationClient, never()).notifyFileTransfer(any());
    }
}
