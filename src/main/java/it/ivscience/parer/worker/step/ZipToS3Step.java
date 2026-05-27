package it.ivscience.parer.worker.step;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.content.service.SipFileService;
import it.ivscience.parer.content.service.SipPackageService;
import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.parer.model.DepositedFile;
import it.ivscience.parer.worker.s3.S3FileMetadata;
import it.ivscience.parer.worker.s3.S3Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pipeline step: streams a ZIP from the source S3 bucket to a separate destination S3 bucket.
 * Alternative to ZipToFtpStep for environments without FTP access.
 *
 * Runs standalone (without sipBuildStep): derives parerKey from sipId and enumerates
 * source files directly from S3 when ctx.getFiles() is not populated.
 * To enable: replace {@code <ref bean="zipToFtpStep"/>} with
 * {@code <ref bean="zipToS3Step"/>} in worker-services.xml.
 */
public class ZipToS3Step implements PipelineStep {

    private static final Logger log = LogManager.getLogger(ZipToS3Step.class);

    @Autowired private S3Service         s3Service;
    @Autowired private SipPackageService sipPackageService;
    @Autowired private SipFileService    sipFileService;

    private String processingStatus;
    private String failedStatus;
    private String destinationBucket;
    private String destinationPrefix = "zip";

    public void setProcessingStatus(String v)  { this.processingStatus  = v; }
    public void setFailedStatus(String v)      { this.failedStatus      = v; }
    public void setDestinationBucket(String v) { this.destinationBucket = v; }
    public void setDestinationPrefix(String v) { this.destinationPrefix = v; }

    @Override public boolean isConsumable() { return true; }

    @Override
    public void consume(PipelineContext ctx) {
        String     sipId        = ctx.getSipId();
        SipPackage pkg          = ctx.getSipPackage();
        String     sourceBucket = ctx.getMsg().getBucket();
        String     dirPrefix    = ctx.getDirPrefix();

        // Derive parerKey and ZIP file name without requiring sipBuildStep
        String parerKey    = ctx.getParerKey() != null ? ctx.getParerKey() : sipId;
        String zipFileName = parerKey + ".zip";
        String destKey     = destinationPrefix + "/" + zipFileName;

        // If sipBuildStep ran and produced sipXml, upload it to the flat dir so it can be a ZIP entry.
        String sipXmlFileName = ctx.getSipXmlFileName() != null ? ctx.getSipXmlFileName() : parerKey + ".xml";
        String sipXmlKey      = dirPrefix + sipXmlFileName;
        if (ctx.getSipXml() != null) {
            byte[] bytes = ctx.getSipXml().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            s3Service.putBytes(sourceBucket, sipXmlKey, bytes, "application/xml");
            log.debug("sipId={} SIP XML uploaded to S3: {}", sipId, sipXmlKey);
        }

        // Build source key list: prefer files already resolved by sipBuildStep,
        // otherwise enumerate directly from S3.
        List<String> sourceKeys = resolveSourceKeys(ctx, sourceBucket, dirPrefix, sipXmlKey, ctx.getSipXml() != null);
        if (sourceKeys.isEmpty()) {
            log.error("sipId={} no source files found in S3 prefix {}", sipId, dirPrefix);
            throw new FunctionalException("No source files found for sipId=" + sipId + " in " + dirPrefix);
        }

        // Idempotency: if all SipFile records are already marked sent, skip ZIP creation.
        List<SipFile> files = ctx.getFiles();
        if (files != null && !files.isEmpty() && files.stream().allMatch(f -> f.getSentAt() != null)) {
            log.info("sipId={} ZIP already created in S3, skipping", sipId);
            ctx.setDepositedFiles(new ArrayList<>(List.of(new DepositedFile("FilePrincipale", zipFileName))));
            return;
        }

        sipPackageService.updateStatus(pkg, processingStatus, null);

        try {
            s3Service.createZip(sourceBucket, sourceKeys, parerKey, destinationBucket, destKey);
        } catch (FunctionalException | TransientException e) {
            sipPackageService.updateStatus(pkg, failedStatus, e.getMessage());
            throw e;
        }

        S3FileMetadata zipMeta = s3Service.headObject(destinationBucket, destKey);
        if (zipMeta == null) {
            String errMsg = "ZIP not found in destination S3 after zipping: s3://" + destinationBucket + "/" + destKey;
            sipPackageService.updateStatus(pkg, failedStatus, errMsg);
            throw new TransientException(errMsg);
        }

        // Mark sent only if we have SipFile records (i.e. sipBuildStep ran)
        if (files != null && !files.isEmpty()) {
            OffsetDateTime now = OffsetDateTime.now();
            files.stream().filter(f -> f.getSentAt() == null)
                    .forEach(sf -> sipFileService.markSent(sf, now, zipFileName, ""));
        }

        log.info("sipId={} ZIP written to s3://{}/{} ({} bytes)", sipId, destinationBucket, destKey, zipMeta.getSizeBytes());
        ctx.setDepositedFiles(new ArrayList<>(List.of(new DepositedFile("FilePrincipale", zipFileName))));
    }

    /**
     * Returns S3 keys to include in the ZIP.
     * If sipBuildStep already resolved SipFile records, uses those (avoids a second S3 list call).
     * Otherwise lists objects from the S3 flat directory directly.
     */
    private List<String> resolveSourceKeys(PipelineContext ctx, String sourceBucket, String dirPrefix,
                                           String sipXmlKey, boolean sipXmlUploaded) {
        List<SipFile> files = ctx.getFiles();
        if (files != null && !files.isEmpty()) {
            List<String> keys = files.stream()
                    .map(sf -> parseS3Uri(sf.getS3Uri())[1])
                    .collect(Collectors.toList());
            if (sipXmlUploaded) {
                keys.add(sipXmlKey);
            }
            return keys;
        }
        // Standalone: enumerate all objects from S3 flat directory (includes any pre-existing SIP XML)
        return s3Service.listObjects(sourceBucket, dirPrefix).stream()
                .map(m -> dirPrefix + m.getFileName())
                .collect(Collectors.toList());
    }

    private static String[] parseS3Uri(String s3Uri) {
        String path = s3Uri.substring("s3://".length());
        int sep = path.indexOf('/');
        return new String[]{ path.substring(0, sep), path.substring(sep + 1) };
    }
}
