package it.ivscience.parer.it;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SipValidatorXsdIT extends AbstractPipelineIT {

    private static final String INVALID_SIP_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><notAValidSip/></root>";

    @Test
    void xsdValidationFailureSetsFailedPreingestAndReturnsExitZero() {
        String sipId      = "IT-XSD-FAIL-" + UUID.randomUUID();
        String processKey = processKeyFor(sipId);

        doReturn(INVALID_SIP_XML).when(sipBuilder).build(any(), any(), any());

        int exit = runBatch("test-bucket", processKey);

        assertThat(exit).isEqualTo(0);
        var pkg = sipPackageService.findBySipId(sipId);
        assertThat(pkg).isPresent();
        assertThat(pkg.get().getStatus()).isEqualTo("FAILED_PREINGEST");
        assertThat(pkg.get().getStatusReason()).containsIgnoringCase("XSD");
        verify(preIngestClient, never()).sendPreIngest(any());
    }
}
