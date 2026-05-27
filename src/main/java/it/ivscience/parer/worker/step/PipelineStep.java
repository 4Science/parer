package it.ivscience.parer.worker.step;

/**
 * A single stage in the SIP ingestion pipeline.
 *
 * <h3>Independence principle</h3>
 * Each step must be as self-sufficient as possible: it should derive what it
 * needs from the base context fields that {@link it.ivscience.parer.worker.SipPipeline}
 * always populates ({@code sipId}, {@code msg}, {@code dirPrefix}, {@code sipPackage},
 * {@code parerKey}, {@code sipXmlFileName}) rather than assuming a specific predecessor
 * step has run.
 *
 * <p>Concrete guidance:
 * <ul>
 *   <li>If a step needs {@code files}: check {@code ctx.getFiles()} first; if null/empty,
 *       query the DB via {@code SipFileService.findBySipPackage()}.</li>
 *   <li>If a step needs {@code depositedFiles}: check {@code ctx.getDepositedFiles()};
 *       if empty, build from the sent {@code SipFile} records already in the DB.</li>
 *   <li>If a step genuinely cannot proceed without a complex artifact (e.g. the raw
 *       SIP XML for pre-ingest), fail fast with a clear message naming the missing
 *       predecessor step.</li>
 *   <li>Update {@code ctx} with any derived values (call {@code ctx.setFiles(...)},
 *       etc.) so subsequent steps skip redundant lookups.</li>
 * </ul>
 */
public interface PipelineStep {
    void consume(PipelineContext ctx);
    boolean isConsumable();
}
