package it.ivscience.parer.worker;

import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.content.service.SipPackageService;
import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.model.WorkUnitDescriptor;
import it.ivscience.parer.worker.s3.S3Service;
import it.ivscience.parer.worker.step.PipelineStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SipPipelineTest {

    @Mock private SipPackageService sipPackageService;
    @Mock private S3Service         s3Service;
    @Mock private PipelineStep      step;

    @InjectMocks
    private SipPipeline sipPipeline;

    @BeforeEach
    void setUp() {
        sipPipeline.setSteps(List.of(step));
        when(step.isConsumable()).thenReturn(true);
    }

    @Test
    void processSkipsAlreadySuccessful() {
        SipPackage pkg = sipPackage("SIP-001", "SUCCESS");
        when(sipPackageService.findBySipId("SIP-001")).thenReturn(Optional.of(pkg));
        sipPipeline.process(sqsMessage("SIP-001"));
        verify(step, never()).consume(any());
        verify(sipPackageService, never()).markSuccess(any());
    }

    @Test
    void processHappyPath() {
        when(sipPackageService.findBySipId("SIP-002")).thenReturn(Optional.empty());
        SipPackage pkg = sipPackage("SIP-002", "CREATED");
        when(sipPackageService.create(eq("SIP-002"), any())).thenReturn(pkg);
        sipPipeline.process(sqsMessage("SIP-002"));
        verify(step).consume(any());
        verify(sipPackageService).markSuccess(pkg);
    }

    @Test
    void processSkipsNonConsumableStep() {
        when(sipPackageService.findBySipId("SIP-003")).thenReturn(Optional.empty());
        SipPackage pkg = sipPackage("SIP-003", "CREATED");
        when(sipPackageService.create(any(), any())).thenReturn(pkg);
        when(step.isConsumable()).thenReturn(false);
        sipPipeline.process(sqsMessage("SIP-003"));
        verify(step, never()).consume(any());
    }

    @Test
    void processThrowsFunctionalWhenStepFails() {
        when(sipPackageService.findBySipId("SIP-004")).thenReturn(Optional.empty());
        SipPackage pkg = sipPackage("SIP-004", "CREATED");
        when(sipPackageService.create(any(), any())).thenReturn(pkg);
        doThrow(new FunctionalException("step KO")).when(step).consume(any());
        assertThrows(FunctionalException.class, () -> sipPipeline.process(sqsMessage("SIP-004")));
    }

    @Test
    void processThrowsTransientWhenStepThrowsTransient() {
        when(sipPackageService.findBySipId("SIP-005")).thenReturn(Optional.empty());
        SipPackage pkg = sipPackage("SIP-005", "CREATED");
        when(sipPackageService.create(any(), any())).thenReturn(pkg);
        doThrow(new TransientException("FTP down")).when(step).consume(any());
        assertThrows(TransientException.class, () -> sipPipeline.process(sqsMessage("SIP-005")));
    }

    private WorkUnitDescriptor sqsMessage(String objectId) {
        WorkUnitDescriptor msg = new WorkUnitDescriptor();
        msg.setObjectId(objectId);
        msg.setBucket("bucket");
        msg.setKey("key");
        return msg;
    }

    private SipPackage sipPackage(String sipId, String status) {
        SipPackage pkg = new SipPackage();
        pkg.setSipId(sipId);
        pkg.setStatus(status);
        return pkg;
    }
}
