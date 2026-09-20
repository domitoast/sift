package dev.sift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Gemini endpoint, model and timeout.
 */
@ConfigurationProperties(prefix = "sift.summarize.gemini")
public record GeminiProperties(
        String baseUrl,
        String model,
        Duration timeout,
        int maxChars
) {
}
