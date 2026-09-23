package dev.sift.auth;

import dev.sift.config.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the cookie that carries the refresh token.
 *
 * HttpOnly keeps it out of reach of page scripts, so an XSS bug can steal at
 * most a 15-minute access token. SameSite=Strict stops other sites from making
 * the browser attach it. The path limits it to the auth endpoints, so it is not
 * sent with every API call.
 */
@Component
public class RefreshCookie {
    public static final String NAME = "sift_refresh";

    static final String PATH = "/api/v1/auth";

    private final Duration maxAge;
    private final boolean secure;

    public RefreshCookie(JwtProperties properties) {
        this.maxAge = Duration.ofDays(properties.refreshTokenTtlDays());
        this.secure = properties.refreshCookieSecure();
    }

    public ResponseCookie issue(String rawToken) {
        return base(rawToken).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
