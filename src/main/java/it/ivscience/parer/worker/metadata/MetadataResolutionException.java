package it.ivscience.parer.worker.metadata;

/**
 * Exception thrown when metadata resolution fails
 * in a non-recoverable way (object not found, malformed response, etc.).
 */
public class MetadataResolutionException extends RuntimeException {

    public MetadataResolutionException(String message) {
        super(message);
    }

    public MetadataResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
