package it.ivscience.parer.worker.s3.impl;

import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.s3.S3FileMetadata;
import it.ivscience.parer.worker.s3.S3Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Checksum;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectAttributesRequest;
import software.amazon.awssdk.services.s3.model.GetObjectAttributesResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectAttributes;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Single S3 service implementation: file I/O and ZIP creation.
 *
 * Wiring XML: s3-services.xml.
 */
public class S3ServiceImpl implements S3Service {

    private static final Logger log = LogManager.getLogger(S3ServiceImpl.class);

    private static final int PIPE_BUFFER_BYTES = 8 * 1024 * 1024;
    private static final int PART_SIZE_BYTES   = 8 * 1024 * 1024;

    private static final Map<String, String> MIME_BY_EXT = Map.ofEntries(
            Map.entry("pdf",  "application/pdf"),
            Map.entry("xml",  "application/xml"),
            Map.entry("tif",  "image/tiff"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("jpg",  "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png",  "image/png"),
            Map.entry("txt",  "text/plain"),
            Map.entry("csv",  "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("bz2",  "application/x-bzip2"),
            Map.entry("gz",   "application/gzip")
    );

    @Autowired
    private S3Client s3Client;

    // ── File I/O ──────────────────────────────────────────────────────────────

    @Override
    public S3FileMetadata headObject(String bucket, String key) {
        HeadObjectResponse head = s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(key).build());
        String fileName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        String mimeType = head.contentType() != null ? head.contentType() : "application/octet-stream";
        long size = head.contentLength() != null ? head.contentLength() : 0L;
        GetObjectAttributesResponse attrs = s3Client.getObjectAttributes(
                GetObjectAttributesRequest.builder()
                        .bucket(bucket).key(key)
                        .objectAttributes(ObjectAttributes.CHECKSUM)
                        .build());
        String checksum = extractChecksum(attrs.checksum());
        log.debug("S3 HEAD s3://{}/{} size={} mime={} checksum={}", bucket, key, size, mimeType, checksum);
        return new S3FileMetadata(bucket, key, fileName, size, mimeType, checksum);
    }

    @Override
    public List<S3FileMetadata> listObjects(String bucket, String prefix) {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
        List<S3FileMetadata> result = new ArrayList<>();
        s3Client.listObjectsV2Paginator(request).contents().forEach(obj -> {
            String key = obj.key();
            if (key.endsWith("/")) return;
            try {
                HeadObjectResponse head = s3Client.headObject(
                        HeadObjectRequest.builder().bucket(bucket).key(key).build());
                String fileName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
                String mimeType = head.contentType() != null ? head.contentType() : guessMimeType(fileName);
                long size = head.contentLength() != null ? head.contentLength() : (obj.size() != null ? obj.size() : 0L);
                String checksum = head.metadata() != null ? head.metadata().getOrDefault("checksum", null) : null;
                log.debug("S3 LIST s3://{}/{} size={} mime={} checksum={}", bucket, key, size, mimeType, checksum);
                result.add(new S3FileMetadata(bucket, key, fileName, size, mimeType, checksum));
            } catch (NoSuchKeyException e) {
                log.warn("S3 file disappeared during listing: s3://{}/{}, skipping", bucket, key);
            }
        });
        return result;
    }

    @Override
    public String streamToConsumer(String bucket, String key, Consumer<InputStream> streamConsumer) {
        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestingInputStream digestingStream = new DigestingInputStream(s3Stream, digest);
            streamConsumer.accept(digestingStream);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException("Error reading S3 s3://" + bucket + "/" + key, e);
        }
    }

    @Override
    public String readString(String bucket, String key) {
        try {
            return s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build()
            ).asUtf8String();
        } catch (NoSuchKeyException e) {
            throw new FunctionalException("S3 object not found: s3://" + bucket + "/" + key, e);
        } catch (SdkClientException e) {
            throw new TransientException("S3 read error for s3://" + bucket + "/" + key, e);
        }
    }

    @Override
    public void putBytes(String bucket, String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket).key(key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .checksumAlgorithm(ChecksumAlgorithm.CRC64_NVME)
                        .build(),
                RequestBody.fromBytes(content));
        log.debug("S3 PUT s3://{}/{} size={} type={}", bucket, key, content.length, contentType);
    }

    // ── ZIP creation ──────────────────────────────────────────────────────────

    @Override
    public String createZip(String sourceBucket, List<String> sourceKeys,
                            String entryFolderName,
                            String destinationBucket, String destinationKey) {

        log.info("Java ZIP: creating s3://{}/{} from {} source files (folder={})",
                destinationBucket, destinationKey, sourceKeys.size(), entryFolderName);
        String folderPrefix = entryFolderName != null && !entryFolderName.isBlank()
                ? entryFolderName + "/" : "";

        CreateMultipartUploadResponse multipart = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(destinationBucket).key(destinationKey)
                        .contentType("application/zip")
                        .build());
        String uploadId = multipart.uploadId();

        List<CompletedPart> completedParts = new ArrayList<>();
        AtomicReference<Exception> uploadError = new AtomicReference<>();

        PipedInputStream  pipedIn;
        PipedOutputStream pipedOut;
        try {
            pipedIn  = new PipedInputStream(PIPE_BUFFER_BYTES);
            pipedOut = new PipedOutputStream(pipedIn);
        } catch (IOException e) {
            abortQuietly(destinationBucket, destinationKey, uploadId);
            throw new TransientException("Failed to create pipe for ZIP streaming: " + e.getMessage(), e);
        }

        Thread uploadThread = Thread.ofVirtual().start(() -> {
            try {
                byte[] chunk = new byte[PART_SIZE_BYTES];
                int partNumber = 1;
                int totalRead;
                while ((totalRead = readFully(pipedIn, chunk)) > 0) {
                    byte[] data = totalRead == chunk.length ? chunk : Arrays.copyOf(chunk, totalRead);
                    UploadPartResponse resp = s3Client.uploadPart(
                            UploadPartRequest.builder()
                                    .bucket(destinationBucket).key(destinationKey)
                                    .uploadId(uploadId).partNumber(partNumber)
                                    .contentLength((long) data.length)
                                    .build(),
                            RequestBody.fromBytes(data));
                    completedParts.add(CompletedPart.builder().partNumber(partNumber).eTag(resp.eTag()).build());
                    log.debug("Java ZIP: uploaded part {} ({} bytes)", partNumber, data.length);
                    partNumber++;
                }
            } catch (Exception e) {
                uploadError.set(e);
            }
        });

        try (ZipOutputStream zip = new ZipOutputStream(pipedOut)) {
            for (String key : sourceKeys) {
                String baseName  = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
                String entryName = folderPrefix + baseName;
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream s3Stream = s3Client.getObject(
                        GetObjectRequest.builder().bucket(sourceBucket).key(key).build())) {
                    s3Stream.transferTo(zip);
                }
                zip.closeEntry();
                log.debug("Java ZIP: packed entry {}", entryName);
            }
        } catch (Exception e) {
            abortQuietly(destinationBucket, destinationKey, uploadId);
            throw new TransientException("Java ZIP streaming failed: " + e.getMessage(), e);
        }

        try { uploadThread.join(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            abortQuietly(destinationBucket, destinationKey, uploadId);
            throw new TransientException("Java ZIP upload interrupted", e);
        }

        if (uploadError.get() != null) {
            abortQuietly(destinationBucket, destinationKey, uploadId);
            throw new TransientException("Java ZIP multipart upload error: " + uploadError.get().getMessage(), uploadError.get());
        }
        if (completedParts.isEmpty()) {
            abortQuietly(destinationBucket, destinationKey, uploadId);
            throw new TransientException("Java ZIP produced zero parts — likely empty source files");
        }

        s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(destinationBucket).key(destinationKey).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build());

        log.info("Java ZIP: completed s3://{}/{} ({} parts)", destinationBucket, destinationKey, completedParts.size());
        return destinationKey;
    }

    @Override
    public void writeSipXml(String bucket, String key, String sipXml) {
        putBytes(bucket, key, sipXml.getBytes(StandardCharsets.UTF_8), "application/xml");
    }

    @Override
    public void writeClientLog(String uploadBucket, String logKey, String errorMessage) {
        try {
            byte[] bytes = (errorMessage == null ? "" : errorMessage).getBytes(StandardCharsets.UTF_8);
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(uploadBucket).key(logKey)
                    .contentType("text/plain").contentLength((long) bytes.length)
                    .build(), RequestBody.fromBytes(bytes));
            log.debug("Client log written s3://{}/{}", uploadBucket, logKey);
        } catch (Exception e) {
            log.warn("Failed to write client log s3://{}/{}: {}", uploadBucket, logKey, e.getMessage());
        }
    }

    @Override
    public void writeFile(String uploadBucket, String logKey, String filePath) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(uploadBucket)
                            .key(logKey)
                            .contentType("text/plain")
                            .build(),
                    RequestBody.fromFile(Path.of(filePath)));
            log.debug("File uploaded s3://{}/{} from {}", uploadBucket, logKey, filePath);
        } catch (Exception e) {
            log.warn("Failed to upload file {} to s3://{}/{}: {}", filePath, uploadBucket, logKey, e.getMessage());
        }
    }

    private static String extractChecksum(Checksum c) {
        if (c == null) return null;
        if (c.checksumSHA256()    != null) return c.checksumSHA256();
        if (c.checksumCRC32()     != null) return c.checksumCRC32();
        if (c.checksumCRC32C()    != null) return c.checksumCRC32C();
        if (c.checksumCRC64NVME() != null) return c.checksumCRC64NVME();
        if (c.checksumSHA1()      != null) return c.checksumSHA1();
        return null;
    }

    private static String guessMimeType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            String ext = fileName.substring(dot + 1).toLowerCase();
            return MIME_BY_EXT.getOrDefault(ext, "application/octet-stream");
        }
        return "application/octet-stream";
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n == -1) break;
            total += n;
        }
        return total;
    }

    private void abortQuietly(String bucket, String key, String uploadId) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket).key(key).uploadId(uploadId).build());
        } catch (Exception ignored) {}
    }

    private static class DigestingInputStream extends InputStream {
        private final InputStream delegate;
        private final MessageDigest digest;

        DigestingInputStream(InputStream delegate, MessageDigest digest) {
            this.delegate = delegate; this.digest = digest;
        }

        @Override public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) digest.update((byte) b);
            return b;
        }

        @Override public int read(byte[] buf, int off, int len) throws IOException {
            int n = delegate.read(buf, off, len);
            if (n > 0) digest.update(buf, off, n);
            return n;
        }

        @Override public void close() throws IOException { delegate.close(); }
    }
}
