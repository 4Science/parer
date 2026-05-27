package it.ivscience.parer.worker.step;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.content.service.SipFileService;
import it.ivscience.parer.content.service.SipPackageService;
import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.parer.NotificationClient;
import it.ivscience.parer.worker.parer.model.DepositedFile;
import it.ivscience.parer.worker.parer.model.NotificationRequest;
import it.ivscience.parer.worker.parer.model.NotificationResponse;
import it.ivscience.parer.worker.sip.builder.FileClassifier;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

public class NotificationStep implements PipelineStep {

    @Autowired private NotificationClient notificationClient;
    @Autowired private SipPackageService  sipPackageService;
    @Autowired private SipFileService     sipFileService;
    @Autowired private FileClassifier     fileClassifier;

    private String environment;
    private String submitter;
    private String fileType;
    private String processingStatus;
    private String failedStatus;

    public void setEnvironment(String v)       { this.environment      = v; }
    public void setSubmitter(String v)         { this.submitter        = v; }
    public void setFileType(String v)          { this.fileType         = v; }
    public void setProcessingStatus(String v)  { this.processingStatus = v; }
    public void setFailedStatus(String v)      { this.failedStatus     = v; }

    @Override public boolean isConsumable() { return true; }

    @Override
    public void consume(PipelineContext ctx) {
        String     sipId = ctx.getSipId();
        SipPackage pkg   = ctx.getSipPackage();
        String     parerKey = ctx.getParerKey();

        List<SipFile> files = ctx.getFiles();
        if (files == null) {
            files = sipFileService.findBySipPackage(pkg);
        }

        List<DepositedFile> depositedFiles = ctx.getDepositedFiles();
        if (depositedFiles.isEmpty() && !files.isEmpty()) {
            List<SipFile> sent = files.stream().filter(f -> f.getRemoteFileName() != null).toList();
            depositedFiles = sent.stream()
                    .map(f -> new DepositedFile(
                            fileType,
                            f.getRemoteFileName()))
                    .collect(Collectors.toList());
        } else {
            for (DepositedFile df : depositedFiles) {
                df.setFileRole(fileType);
            }
        }

        sipPackageService.updateStatus(pkg, processingStatus, null);

        NotificationRequest req = buildRequest(parerKey, depositedFiles);
        NotificationResponse res = notificationClient.notifyFileTransfer(req);

        if (!res.isOk()) {
            String reason = res.getErrorCode() + ": " + res.getErrorMessage();
            sipPackageService.updateStatus(pkg, failedStatus, reason);
            throw new FunctionalException("notifyFileTransfer KO for sipId=" + sipId + " — " + reason);
        }
    }

    private NotificationRequest buildRequest(String parerKey, List<DepositedFile> files) {
        NotificationRequest req = new NotificationRequest();
        req.setEnvironment(environment);
        req.setSubmitter(submitter);
        req.setObjectKey(parerKey);
        req.setDepositedFiles(files);
        return req;
    }

}
