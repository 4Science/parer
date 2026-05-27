package it.ivscience.parer.worker.errors;

/**
 * Exception for transient errors (network, timeout, HTTP 5xx, temporary I/O).
 * The SQS message IS NOT deleted: it will become visible again upon
 * visibility timeout expiration and will be automatically re-processed.
 */
public class TransientException extends RuntimeException {

    public TransientException(String message) {
        super(message);
    }

    public TransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
