package it.ivscience.parer.worker;

import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.content.service.SipPackageService;
import it.ivscience.parer.worker.model.WorkUnitDescriptor;
import it.ivscience.parer.worker.step.PipelineContext;
import it.ivscience.parer.worker.step.PipelineStep;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator of the ingestion pipeline for a single SIP object.
 *
 * Responsibilities:
 *  1. Idempotency check (skip if already SUCCESS)
 *  2. Create or resume SipPackage
 *  3. Build PipelineContext with base fields from the incoming message
 *  4. Iterate over configured PipelineStep beans in order
 *  5. Mark SUCCESS
 */
public class SipPipeline {

    private static final Logger log = LogManager.getLogger(SipPipeline.class);

    @Autowired private SipPackageService sipPackageService;

    private List<PipelineStep> steps = new ArrayList<>();
    private String contentPrefix = "parer/inserimento/";
    private String successStatus = "SUCCESS";

    public void setSteps(List<PipelineStep> steps)  { this.steps         = steps; }
    public void setContentPrefix(String v)           { this.contentPrefix = v; }
    public void setSuccessStatus(String v)           { this.successStatus = v; }

    public void process(WorkUnitDescriptor msg) {
        String sipId = msg.getObjectId();
        log.info("Starting pipeline for sipId={}", sipId);

        // 1. Idempotency: skip if already SUCCESS
        SipPackage sipPackage = sipPackageService.findBySipId(sipId).orElse(null);
        if (sipPackage != null && successStatus.equals(sipPackage.getStatus())) {
            log.info("sipId={} already in SUCCESS state, skipping", sipId);
            return;
        }

        // 2. Create or resume SipPackage
        if (sipPackage == null) {
            sipPackage = sipPackageService.create(sipId, "s3://" + msg.getBucket() + "/" + msg.getKey());
            log.info("Created SipPackage id={} sipId={}", sipPackage.getId(), sipId);
        }

        // 3. Build context with base fields
        PipelineContext ctx = new PipelineContext(msg);
        ctx.setSipPackage(sipPackage);
        ctx.setDirPrefix(contentPrefix + sipId + "/");
        ctx.setParerKey(sipId);
        ctx.setSipXmlFileName(sipId + ".xml");

        // 4. Run configured steps
        for (PipelineStep step : steps) {
            if (step.isConsumable()) {
                step.consume(ctx);
            }
        }

        // 5. SUCCESS
        sipPackageService.markSuccess(sipPackage);
        log.info("Pipeline completed with SUCCESS for sipId={}", sipId);
    }
}
