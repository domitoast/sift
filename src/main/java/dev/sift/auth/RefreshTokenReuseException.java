package dev.sift.auth;

/**
 * A revoked token was presented again, which means it leaked.
 * The whole token chain for that user is revoked in response.
 */
public class RefreshTokenReuseException extends InvalidRefreshTokenException {
}
