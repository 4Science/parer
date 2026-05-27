package it.ivscience.parer.worker.step;

import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.content.service.SipPackageService;
import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.model.SipStatusRegistry;
import it.ivscience.parer.worker.parer.PreIngestClient;
import it.ivscience.parer.worker.parer.model.PreIngestRequest;
import it.ivscience.parer.worker.parer.model.PreIngestResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

public class PreIngestStep implements PipelineStep {

    private static final Logger log = LogManager.getLogger(PreIngestStep.class);

    @Autowired private PreIngestClient   preIngestClient;
    @Autowired private SipPackageService sipPackageService;
    @Autowired private SipStatusRegistry statusRegistry;

    private String environment;
    private String submitter;
    private String objectType;
    private String processingStatus;
    private String failedStatus;

    public void setEnvironment(String v)       { this.environment     = v; }
    public void setSubmitter(String v)         { this.submitter       = v; }
    public void setObjectType(String v)        { this.objectType      = v; }
    public void setProcessingStatus(String v)  { this.processingStatus = v; }
    public void setFailedStatus(String v)      { this.failedStatus    = v; }

    @Override public boolean isConsumable() { return true; }

    @Override
    public void consume(PipelineContext ctx) {
        String     sipId    = ctx.getSipId();
        SipPackage pkg      = ctx.getSipPackage();
        String     parerKey = ctx.getParerKey();

        if (isAfterPreIngest(pkg.getStatus())) {
            log.info("sipId={} STEP 1 already completed, skipping sendPreIngest", sipId);
            return;
        }

        sipPackageService.updateStatus(pkg, processingStatus, null);

        PreIngestRequest req = buildRequest(parerKey);
        PreIngestResponse res = preIngestClient.sendPreIngest(req);

        if (!res.isOk() && !res.isWarn()) {
            String reason = res.getErrorCode() + ": " + res.getErrorMessage();
            if (isAlreadyRegisteredError(res)) {
                log.warn("sipId={} already registered on ParER ({}), treating as idempotent success", sipId, reason);
            } else {
                sipPackageService.updateStatus(pkg, failedStatus, reason);
                throw new FunctionalException("sendPreIngest KO for sipId=" + sipId + " — " + reason);
            }
        }
    }

    private PreIngestRequest buildRequest(String parerKey) {
        PreIngestRequest req = new PreIngestRequest();
        req.setEnvironment(environment);
        req.setSubmitter(submitter);
        req.setObjectKey(parerKey);
        req.setObjectType(objectType);
        req.setFileCiphered(false);
        req.setForceWarning(false);
        req.setForceAcceptance(false);
        return req;
    }

    private boolean isAfterPreIngest(String status) {
        return statusRegistry.isAfter(status, processingStatus);
    }

    private static boolean isAlreadyRegisteredError(PreIngestResponse res) {
        String code = res.getErrorCode();
        String msg  = res.getErrorMessage();
        if (code == null) return false;
        return code.contains("DUPKEY")
            || code.contains("001-0001")
            || (msg != null && msg.toLowerCase().contains("già presente"))
            || (msg != null && msg.toLowerCase().contains("already registered"));
    }
}
