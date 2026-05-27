package it.ivscience.parer.worker.s3;

import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Single S3 service interface: file I/O and ZIP creation.
 *
 * Replaces the former S3FileReader and S3ZipperClient interfaces.
 * All operations are idempotent (PutObject with same key overwrites).
 */
public interface S3Service {

    // ── File I/O ──────────────────────────────────────────────────────────────

    S3FileMetadata headObject(String bucket, String key);

    List<S3FileMetadata> listObjects(String bucket, String prefix);

    String streamToConsumer(String bucket, String key, Consumer<InputStream> streamConsumer);

    String readString(String bucket, String key);

    void putBytes(String bucket, String key, byte[] content, String contentType);

    void writeSipXml(String bucket, String key, String sipXml);

    // ── ZIP creation ──────────────────────────────────────────────────────────

    /**
     * Streams source files into a ZIP and uploads it to destinationBucket/destinationKey.
     * Entries are placed under {@code entryFolderName/} inside the ZIP.
     *
     * @throws FunctionalException on permanent errors
     * @throws TransientException  on retriable errors
     */
    String createZip(String sourceBucket, List<String> sourceKeys,
                     String entryFolderName,
                     String destinationBucket, String destinationKey)
            throws FunctionalException, TransientException;

    void writeFile(String uploadBucket, String logKey, String filePath);

    void writeClientLog(String uploadBucket, String logKey, String errorMessage);
}
