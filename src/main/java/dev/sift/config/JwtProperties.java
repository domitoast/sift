package dev.sift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Signing key and token lifetimes.
 */
@ConfigurationProperties(prefix = "sift.jwt")
public record JwtProperties(
        String secret,
        int accessTokenTtlMinutes,
        int refreshTokenTtlDays
) {
}
