package it.ivscience.parer.it;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SipPipelinePreIngestKoIT extends AbstractPipelineIT {

    @Test
    void preIngestKoSetsFailedStatusAndReturnsExitZero() {
        String sipId      = "IT-PREINGEST-KO-" + UUID.randomUUID();
        String processKey = processKeyFor(sipId);

        doReturn(preIngestKo("ERR-001", "Invalid object type"))
                .when(preIngestClient).sendPreIngest(any());

        int exit = runBatch("test-bucket", processKey);

        assertThat(exit).isEqualTo(0);
        var pkg = sipPackageService.findBySipId(sipId);
        assertThat(pkg).isPresent();
        assertThat(pkg.get().getStatus()).isEqualTo("FAILED_PREINGEST");
        assertThat(pkg.get().getStatusReason()).contains("ERR-001");
        verify(ftpClient, never()).upload(any(), any(), any(), anyLong());
    }
}
