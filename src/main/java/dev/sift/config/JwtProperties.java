package dev.sift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Signing key, token lifetimes and refresh-cookie settings.
 *
 * refreshCookieSecure should only be turned off for plain-HTTP access from a
 * host other than localhost (browsers already treat localhost as secure).
 */
@ConfigurationProperties(prefix = "sift.jwt")
public record JwtProperties(
        String secret,
        int accessTokenTtlMinutes,
        int refreshTokenTtlDays,
        boolean refreshCookieSecure
) {
}
