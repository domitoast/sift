package dev.sift.auth;

import dev.sift.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * Issues and verifies access tokens (HS512).
 */
@Service
public class JwtService {
    private static final int MIN_SECRET_BYTES = 64;

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final SecretKey secretKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.secret());

        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT 金鑰長度不足：需要至少 %d bytes，實際為 %d bytes。"
                            .formatted(MIN_SECRET_BYTES, keyBytes.length)
                            + "請以 openssl rand -base64 64 重新產生。");
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtl = Duration.ofMinutes(properties.accessTokenTtlMinutes());
        this.refreshTokenTtl = Duration.ofDays(properties.refreshTokenTtlDays());
    }

    public String generateAccessToken(Long userId) {
        Instant now = Instant.now();

        return Jwts.builder()

                .subject(String.valueOf(userId))

                .issuedAt(Date.from(now))

                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(secretKey)
                .compact();
    }

    public Long extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.valueOf(claims.getSubject());
    }

    public String generateRefreshTokenValue() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("找不到 SHA-256 演算法", e);
        }
    }

    public Instant refreshTokenExpiryFromNow() {
        return Instant.now().plus(refreshTokenTtl);
    }
}
