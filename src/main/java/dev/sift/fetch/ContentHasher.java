package dev.sift.fetch;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the fingerprint used to detect articles we have already stored.
 */
@Component
public class ContentHasher {
    public String hash(String title, String content) {
        String canonical = title + "\n" + (content == null ? "" : content);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("找不到 SHA-256 演算法", e);
        }
    }
}
