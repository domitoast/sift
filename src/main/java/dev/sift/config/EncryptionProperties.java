package dev.sift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加密相關設定，對應 {@code sift.encryption} 區段。
 *
 * @param secret Base64 編碼的 AES 金鑰，必須是 256 bits（解碼後 32 bytes）。
 *               <b>與 JWT 的 secret 是不同的一把</b>——見下方說明
 */
@ConfigurationProperties(prefix = "sift.encryption")
public record EncryptionProperties(String secret) {
}
