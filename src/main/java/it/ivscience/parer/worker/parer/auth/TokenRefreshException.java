package it.ivscience.parer.worker.parer.auth;

/**
 * Thrown when a ParER OAuth2 token cannot be obtained or refreshed.
 */
public class TokenRefreshException extends RuntimeException {

    public TokenRefreshException(String message) {
        super(message);
    }

    public TokenRefreshException(String message, Throwable cause) {
        super(message, cause);
    }
}
