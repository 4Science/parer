package it.ivscience.parer.worker.errors;

/**
 * Exception for non-recoverable functional errors (KO result from ParER,
 * failed XSD validation, malformed message, etc.).
 * The SQS message IS deleted to avoid infinite re-processing:
 * the problem requires manual intervention or correction of the source data.
 */
public class FunctionalException extends RuntimeException {

    public FunctionalException(String message) {
        super(message);
    }

    public FunctionalException(String message, Throwable cause) {
        super(message, cause);
    }
}
