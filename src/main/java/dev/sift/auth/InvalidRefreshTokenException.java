package dev.sift.auth;

/**
 * Refresh token is unknown, expired or already revoked.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("憑證無效，請重新登入");
    }
}
