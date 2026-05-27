package it.ivscience.parer.worker.step;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.content.service.SipFileService;
import it.ivscience.parer.content.service.SipPackageService;
import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.ftp.FtpClient;
import it.ivscience.parer.worker.parer.model.DepositedFile;
import it.ivscience.parer.worker.s3.S3Service;
import it.ivscience.parer.worker.sip.builder.FileClassifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipToFtpStep implements PipelineStep {

    private static final Logger log = LogManager.getLogger(ZipToFtpStep.class);

    @Autowired private FtpClient         ftpClient;
    @Autowired private S3Service         s3Service;
    @Autowired private SipPackageService sipPackageService;
    @Autowired private SipFileService    sipFileService;
    @Autowired private FileClassifier    fileClassifier;

    private String  processingStatus;
    private String  failedStatus;
    private boolean sendSingleFiles = false;
    private int     ftpThreads      = 4;
    private volatile ExecutorService uploadExecutor;

    public void setProcessingStatus(String v)  { this.processingStatus = v; }
    public void setFailedStatus(String v)      { this.failedStatus     = v; }
    public void setSendSingleFiles(boolean v)  { this.sendSingleFiles  = v; }
    public void setFtpThreads(int v)           { this.ftpThreads       = v; }

    @Override public boolean isConsumable() { return true; }

    @Override
    public void consume(PipelineContext ctx) {
        String     sipId    = ctx.getSipId();
        String     parerKey = ctx.getParerKey();
        String     sipXml   = ctx.getSipXml();
        SipPackage pkg      = ctx.getSipPackage();
        List<SipFile> files = ctx.getFiles();
        if (files == null || files.isEmpty()) {
            files = sipFileService.findBySipPackage(pkg);
            if (files.isEmpty()) {
                throw new FunctionalException("No files found for sipId=" + sipId);
            }
            ctx.setFiles(files);
        }

        List<SipFile> pending     = files.stream().filter(f -> f.getSentAt() == null).collect(Collectors.toList());
        List<SipFile> alreadySent = files.stream().filter(f -> f.getSentAt() != null).collect(Collectors.toList());

        List<DepositedFile> deposited;
        if (sendSingleFiles) {
            deposited = transferSingleFiles(parerKey, sipXml, pkg, sipId, pending, alreadySent);
        } else {
            deposited = transferViaZip(
                    ctx.getMsg().getBucket(), ctx.getDirPrefix(),
                    parerKey, sipId, pkg, files, pending, sipXml, ctx.getSipXmlFileName());
        }
        ctx.setDepositedFiles(deposited);
    }

    private List<DepositedFile> transferSingleFiles(
            String parerKey, String sipXml, SipPackage pkg, String sipId,
            List<SipFile> pending, List<SipFile> alreadySent) {

        if (pending.isEmpty()) {
            log.info("sipId={} STEP 2 already completed for all files, skipping FTP upload", sipId);
            List<DepositedFile> deposited = buildDepositedFileList(alreadySent);
            deposited.add(uploadSipXmlViaFtp(parerKey, sipXml));
            return deposited;
        }

        sipPackageService.updateStatus(pkg, processingStatus, null);

        List<CompletableFuture<DepositedFile>> futures = pending.stream()
                .map(sf -> CompletableFuture.supplyAsync(() -> uploadSingleFile(parerKey, sf), getUploadExecutor()))
                .toList();

        List<DepositedFile> deposited;
        try {
            List<DepositedFile> uploaded = CompletableFuture
                    .allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList()))
                    .join();
            deposited = new ArrayList<>(buildDepositedFileList(alreadySent));
            deposited.addAll(uploaded);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            sipPackageService.updateStatus(pkg, failedStatus, cause.getMessage());
            if (cause instanceof FunctionalException fe) throw fe;
            if (cause instanceof TransientException  te) throw te;
            throw new TransientException("FTP transfer failed for sipId=" + sipId, cause);
        }

        if (sipXml != null) {
            deposited.add(uploadSipXmlViaFtp(parerKey, sipXml));
        }
        return deposited;
    }

    private List<DepositedFile> transferViaZip(
            String bucket, String dirPrefix, String parerKey, String sipId,
            SipPackage pkg, List<SipFile> files, List<SipFile> pending,
            String sipXml, String sipXmlFileName) {

        String zipFileName  = parerKey + ".zip";
        String folderPrefix = parerKey + "/";

        if (pending.isEmpty()) {
            log.info("sipId={} STEP 2 already completed (ZIP mode), skipping", sipId);
            return new ArrayList<>(List.of(new DepositedFile("FilePrincipale", zipFileName)));
        }

        // Collect S3 keys: content files + SIP XML
        List<String> sourceKeys = files.stream()
                .map(sf -> parseS3Uri(sf.getS3Uri())[1])
                .collect(Collectors.toList());
        String sipXmlKey = dirPrefix + sipXmlFileName;
        if (sipXml != null) {
            s3Service.putBytes(bucket, sipXmlKey, sipXml.getBytes(StandardCharsets.UTF_8), "application/xml");
        }
        sourceKeys.add(sipXmlKey);

        sipPackageService.updateStatus(pkg, processingStatus, null);

        // Streaming pipe: S3 files → ZipOutputStream → PipedOutputStream → PipedInputStream → FTP
        PipedInputStream  pipedIn;
        PipedOutputStream pipedOut;
        try {
            pipedIn  = new PipedInputStream(8 * 1024 * 1024);
            pipedOut = new PipedOutputStream(pipedIn);
        } catch (IOException e) {
            throw new TransientException("Failed to create ZIP pipe: " + e.getMessage(), e);
        }

        AtomicReference<Exception> zipError = new AtomicReference<>();

        Thread writer = Thread.ofVirtual().start(() -> {
            try (ZipOutputStream zip = new ZipOutputStream(pipedOut)) {
                for (String key : sourceKeys) {
                    String baseName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
                    zip.putNextEntry(new ZipEntry(folderPrefix + baseName));
                    s3Service.streamToConsumer(bucket, key, stream -> {
                        try { stream.transferTo(zip); }
                        catch (IOException ex) { throw new UncheckedIOException(ex); }
                    });
                    zip.closeEntry();
                    log.debug("sipId={} ZIP entry: {}", sipId, baseName);
                }
            } catch (Exception e) {
                zipError.set(e);
            }
        });

        try {
            // size=0: FTP STOR does not require Content-Length; param used for logging only
            ftpClient.upload(parerKey, zipFileName, pipedIn, 0);
        } catch (FunctionalException | TransientException e) {
            sipPackageService.updateStatus(pkg, failedStatus, e.getMessage());
            throw e;
        }

        try { writer.join(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientException("ZIP writer thread interrupted for sipId=" + sipId, e);
        }
        if (zipError.get() != null) {
            sipPackageService.updateStatus(pkg, failedStatus, zipError.get().getMessage());
            throw new TransientException("ZIP streaming failed for sipId=" + sipId, zipError.get());
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (SipFile sf : pending) {
            sipFileService.markSent(sf, now, zipFileName, "");
        }
        log.info("sipId={} ZIP {} streamed directly to FTP (S3→ZIP→FTP)", sipId, zipFileName);
        return new ArrayList<>(List.of(new DepositedFile("FilePrincipale", zipFileName)));
    }

    private DepositedFile uploadSingleFile(String objectCode, SipFile sf) {
        String[] bucketKey = parseS3Uri(sf.getS3Uri());
        if (s3Service.headObject(bucketKey[0], bucketKey[1]) == null) {
            throw new FunctionalException("S3 file not found before FTP upload: " + sf.getS3Uri());
        }
        String remoteFileName = sf.getFileName();
        String checksum = s3Service.streamToConsumer(bucketKey[0], bucketKey[1], stream ->
                ftpClient.upload(objectCode, remoteFileName, stream, sf.getFileSizeBytes()));
        sipFileService.markSent(sf, OffsetDateTime.now(), remoteFileName, checksum != null ? checksum : "");
        log.info("objectCode={} file={} transferred via FTP", objectCode, remoteFileName);
        return new DepositedFile(resolveFileRole(sf.getFileName()), remoteFileName);
    }

    private DepositedFile uploadSipXmlViaFtp(String objectCode, String sipXml) {
        byte[] bytes = sipXml.getBytes(StandardCharsets.UTF_8);
        String remoteFileName = objectCode + ".xml";
        ftpClient.upload(objectCode, remoteFileName, new ByteArrayInputStream(bytes), bytes.length);
        log.info("objectCode={} SIP XML uploaded via FTP as {}", objectCode, remoteFileName);
        return new DepositedFile("FileIndice", remoteFileName);
    }

    private List<DepositedFile> buildDepositedFileList(List<SipFile> sentFiles) {
        return sentFiles.stream()
                .map(sf -> new DepositedFile(resolveFileRole(sf.getFileName()), sf.getRemoteFileName()))
                .collect(Collectors.toList());
    }

    private String resolveFileRole(String fileName) {
        return fileClassifier.isAnnotazione(fileName) ? "FileAnnotazione" : "FilePrincipale";
    }

    private static String[] parseS3Uri(String s3Uri) {
        String path = s3Uri.substring("s3://".length());
        int sep = path.indexOf('/');
        return new String[]{ path.substring(0, sep), path.substring(sep + 1) };
    }

    private ExecutorService getUploadExecutor() {
        if (uploadExecutor == null) {
            synchronized (this) {
                if (uploadExecutor == null) {
                    uploadExecutor = Executors.newFixedThreadPool(ftpThreads);
                }
            }
        }
        return uploadExecutor;
    }
}
