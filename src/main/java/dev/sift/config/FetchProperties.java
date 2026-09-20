package dev.sift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Fetch settings: timeouts, response size cap, SSRF switch, stuck-job threshold.
 */
@ConfigurationProperties(prefix = "sift.fetch")
public record FetchProperties(
        boolean allowInternalAddress,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxBodyBytes,
        Duration stuckTimeout
) {
}
