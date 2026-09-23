package dev.sift.auth;

/**
 * Both halves of a login or refresh. The controller splits them: the access
 * token goes in the response body, the refresh token into a cookie.
 */
public record IssuedTokens(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}
