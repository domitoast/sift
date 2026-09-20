package dev.sift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AES key used to encrypt user-supplied LLM API keys.
 */
@ConfigurationProperties(prefix = "sift.encryption")
public record EncryptionProperties(String secret) {
}
