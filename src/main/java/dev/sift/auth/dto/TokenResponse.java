package dev.sift.auth.dto;

/**
 * A freshly issued access + refresh token pair.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
    public static TokenResponse bearer(String accessToken,
                                       String refreshToken,
                                       long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
