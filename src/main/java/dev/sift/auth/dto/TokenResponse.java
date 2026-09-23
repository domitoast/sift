package dev.sift.auth.dto;

/**
 * A freshly issued access token.
 *
 * The refresh token is deliberately absent: it is sent as an HttpOnly cookie
 * so that page scripts never see it.
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
