package it.ivscience.parer.worker.step;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.service.SipFileService;
import it.ivscience.parer.content.service.SipPackageService;
import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.metadata.MetadataResolutionException;
import it.ivscience.parer.worker.metadata.MetadataResolver;
import it.ivscience.parer.worker.s3.S3FileMetadata;
import it.ivscience.parer.worker.s3.S3Service;
import it.ivscience.parer.worker.sip.Metadata;
import it.ivscience.parer.worker.sip.SipBuilder;
import it.ivscience.parer.worker.sip.SipIndexContext;
import it.ivscience.parer.worker.sip.SipValidationException;
import it.ivscience.parer.worker.sip.SipValidator;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

import static it.ivscience.parer.utils.ParerUtils.getKeyNumber;

public class SipBuildStep implements PipelineStep {

    private static final Logger log = LogManager.getLogger(SipBuildStep.class);

    @Autowired(required = false) private MetadataResolver metadataResolver;
    @Autowired private SipFileService    sipFileService;
    @Autowired private SipPackageService sipPackageService;
    @Autowired private S3Service         s3Service;
    @Autowired private SipBuilder        sipBuilder;
    @Autowired private SipValidator      sipValidator;

    private boolean resolveMetadata      = true;
    private String  failedStatus;
    private String  expectedHandlePrefix = null;

    public void setResolveMetadata(boolean v)     { this.resolveMetadata = v; }
    public void setFailedStatus(String v)         { this.failedStatus    = v; }
    public void setExpectedHandlePrefix(String v) {
        this.expectedHandlePrefix = (v != null && !v.isBlank()) ? v : null;
    }

    @Override public boolean isConsumable() { return true; }

    @Override
    public void consume(PipelineContext ctx) {
        String sipId = ctx.getSipId();

        String number = getKeyNumber(sipId);
        String dspaceHandle     = number.replace('_', '/');

        SipIndexContext metaCtx = null;
        if (StringUtils.startsWith(dspaceHandle, expectedHandlePrefix)) {
            // if dspace object retrieve metadata from dspace
            metaCtx = buildDSpaceContext(dspaceHandle);
        } else {
            // otherwise, build metadata from objectId
            metaCtx = buildNonDSpaceContext(number);
        }

        ctx.setMetadataContext(metaCtx);

        String year    = metaCtx.get("year");
        String regType = metaCtx.get("registryType");
        String fullParerKey   = sipId.contains("^") ? sipId : regType + "^" + year + "^" + sipId;
        String sipXmlFileName = fullParerKey + ".xml";
        ctx.setParerKey(fullParerKey);
        ctx.setSipXmlFileName(sipXmlFileName);

        String bucket    = ctx.getMsg().getBucket();
        String dirPrefix = ctx.getDirPrefix();

        List<SipFile> registered = sipFileService.findBySipPackage(ctx.getSipPackage());
        List<SipFile> contentFiles = registered.stream()
                .filter(f -> !f.getFileName().equals("metadata.xml"))
                .collect(Collectors.toList());
        if (contentFiles.isEmpty()) {
            List<S3FileMetadata> allFiles = s3Service.listObjects(bucket, dirPrefix);
            if (allFiles.isEmpty()) {
                log.error("sipId={} S3 prefix not found or empty: {}", sipId, dirPrefix);
                throw new FunctionalException("S3 prefix not found for sipId=" + sipId + ": " + dirPrefix);
            }
            List<S3FileMetadata> toRegister = allFiles.stream()
                    .filter(m -> !m.getFileName().equals(sipXmlFileName)
                              && !m.getFileName().equals("metadata.xml"))
                    .collect(Collectors.toList());
            if (toRegister.isEmpty()) {
                log.error("sipId={} no content files found in S3 prefix {}", sipId, dirPrefix);
                throw new FunctionalException("sipId=" + sipId + " no content files found in S3");
            }
            for (S3FileMetadata meta : toRegister) {
                S3FileMetadata enriched = meta.getChecksum() != null
                        ? meta
                        : s3Service.headObject(meta.getBucket(), meta.getKey());
                sipFileService.create(ctx.getSipPackage(), enriched);
            }
        }

        boolean metaRegistered = registered.stream().anyMatch(f -> f.getFileName().equals("metadata.xml"));
        if (metaCtx != null && metaCtx.getMetadataXml() != null && !metaRegistered) {
            byte[] xmlBytes    = metaCtx.getMetadataXml().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String metadataKey = dirPrefix + "metadata.xml";
            s3Service.putBytes(bucket, metadataKey, xmlBytes, "application/xml");
            S3FileMetadata metadataMeta = s3Service.headObject(bucket, metadataKey);
            sipFileService.create(ctx.getSipPackage(), metadataMeta);
            log.debug("sipId={} metadata.xml registered checksum={}", sipId, metadataMeta.getChecksum());
        }

        List<SipFile> files = sipFileService.findBySipPackage(ctx.getSipPackage());
        ctx.setFiles(files);

        if (metaCtx == null) metaCtx = new SipIndexContext();
        String sipXml = sipBuilder.build(ctx.getSipPackage(), files, metaCtx);
        s3Service.writeSipXml(bucket, dirPrefix + sipXmlFileName, sipXml);
        try {
            sipValidator.validate(sipXml, sipId);
        } catch (SipValidationException e) {
            sipPackageService.updateStatus(ctx.getSipPackage(), failedStatus,
                    "XSD validation failed: " + e.getMessage());
            throw new FunctionalException("XSD validation failure for sipId=" + sipId, e);
        }
        log.debug("SIP XML generated for sipId={}", sipId);
        ctx.setSipXml(sipXml);
    }

    private SipIndexContext buildDSpaceContext(String handle) {
        SipIndexContext ctx = null;
        if (resolveMetadata && metadataResolver != null) {
            try {
                ctx = metadataResolver.resolve(handle);
            } catch (MetadataResolutionException e) {
                log.error("handle={} not found in DSpace: {}",
                    handle, e.getMessage());
                throw new FunctionalException("Metadata resolution failure for handle=" + handle, e);
            }
        }
        return ctx;
    }

    private SipIndexContext buildNonDSpaceContext(String number) {
        SipIndexContext ctx = new SipIndexContext();
        ctx.add(new Metadata("number",               number));
        ctx.add(new Metadata("year",                 "2001"));
        ctx.add(new Metadata("registryType",         "AMS_HISTORICA"));
        ctx.add(new Metadata("subject",              number));
        ctx.add(new Metadata("sourceSystem",         "non-dspace"));
        ctx.add(new Metadata("handle",               ""));
        ctx.add(new Metadata("collectionId",         ""));
        ctx.add(new Metadata("collectionDescription",""));
        ctx.add(new Metadata("dataProvider",         ""));
        return ctx;
    }
}
