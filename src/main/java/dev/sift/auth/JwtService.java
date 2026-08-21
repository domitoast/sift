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
 * 負責 token 的產生與驗證。
 *
 * <p>這個類別是純粹的技術工具——它不知道任何業務規則，
 * 只做「字串進、字串出」的密碼學操作。
 * 因此它不碰資料庫、不拋業務例外。
 */
@Service
public class JwtService {

    /** HS512 演算法要求金鑰至少 512 bits（64 bytes）。 */
    private static final int MIN_SECRET_BYTES = 64;

    /** refresh token 的隨機位元組數。256 bits 足以讓暴力破解不可行。 */
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final SecretKey secretKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    /**
     * {@link SecureRandom} 是「密碼學等級」的亂數產生器。
     *
     * <p>⚠️ 絕對不可以用 {@code java.util.Random} 產生 token——
     * 它的輸出是可預測的：只要觀察到幾個輸出值，就能推算出後續所有值。
     * 攻擊者因此能預測出別人的 token。
     */
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties properties) {

        byte[] keyBytes = Base64.getDecoder().decode(properties.secret());

        /*
         * 啟動時就檢查金鑰長度，而不是等到第一次簽發 token 才失敗。
         *
         * 若金鑰太短，JJWT 會在簽章時拋例外——那時已經是使用者
         * 按下登入按鈕的當下，而且錯誤訊息會出現在 500 回應裡。
         * 在建構子檢查，問題會在啟動階段就暴露。
         */
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

    // =========================================================================
    // Access token
    // =========================================================================

    /**
     * 產生 access token。
     *
     * <p><b>payload 只放 userId，不放 email 或其他個人資料。</b>
     * 理由有二：
     * <ul>
     *   <li>JWT 沒有加密，任何人都能解開閱讀。放 email 等於公開它</li>
     *   <li>token 每個請求都要傳輸，放越多越浪費頻寬</li>
     * </ul>
     * 需要 email 時，Controller 依 userId 查資料庫即可。
     *
     * @param userId 使用者 id
     * @return 已簽章的 JWT 字串
     */
    public String generateAccessToken(Long userId) {

        Instant now = Instant.now();

        return Jwts.builder()
                // sub（subject）是 JWT 標準欄位，代表「這個 token 在講誰」
                .subject(String.valueOf(userId))
                // iat（issued at）：簽發時間
                .issuedAt(Date.from(now))
                // exp（expiration）：過期時間。驗證時 JJWT 會自動檢查
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 驗證 access token 並取出 userId。
     *
     * <p>{@code parseSignedClaims} 會一次做完三件事：
     * <ol>
     *   <li>用金鑰重新計算簽章，比對是否相符（防竄改）</li>
     *   <li>檢查 exp 是否已過期</li>
     *   <li>解析出 payload 內容</li>
     * </ol>
     * 任何一項失敗都會拋出 {@link JwtException} 的子類別。
     *
     * @param token JWT 字串
     * @return 使用者 id
     * @throws JwtException token 無效、被竄改或已過期
     */
    public Long extractUserId(String token) {

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.valueOf(claims.getSubject());
    }

    // =========================================================================
    // Refresh token
    // =========================================================================

    /**
     * 產生 refresh token 的值。
     *
     * <p><b>刻意「不是」JWT，而是一串隨機字元。</b>
     *
     * <p>為什麼：JWT 的核心優勢是「自我描述」——伺服器不查資料庫
     * 就知道持有者是誰。但我們的 refresh token 本來就要查資料庫
     * （才能判斷是否已被撤銷），這個優勢完全用不上。
     *
     * <p>用隨機字串反而更好：
     * <ul>
     *   <li>更短，減少傳輸與儲存成本</li>
     *   <li>不洩漏任何資訊——JWT 的 payload 是公開可讀的，
     *       隨機字串則什麼都看不出來</li>
     *   <li>無法被離線分析。JWT 至少會透露簽發時間與過期時間</li>
     * </ul>
     *
     * <p>使用 URL-safe Base64 編碼，確保它能安全地放進 HTTP 標頭與網址。
     */
    public String generateRefreshTokenValue() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 計算 refresh token 的 SHA-256 雜湊值，用於儲存與比對。
     *
     * <p>資料庫存的是這個雜湊值，不是 token 本身——
     * 資料庫外洩時攻擊者無法反推出可用的 token（見 V2 migration 的說明）。
     *
     * <p>此處使用 SHA-256 而非 BCrypt：refresh token 是 256 bits 的
     * 隨機值，暴力破解本來就不可行，不需要刻意放慢。
     * 用 BCrypt 只會讓每次 refresh 多等 100 毫秒。
     *
     * @return 64 個字元的十六進位字串，對應資料庫的 CHAR(64)
     */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 平台保證存在的演算法，這個分支實際上不會發生
            throw new IllegalStateException("找不到 SHA-256 演算法", e);
        }
    }

    /**
     * refresh token 的過期時間。
     */
    public Instant refreshTokenExpiryFromNow() {
        return Instant.now().plus(refreshTokenTtl);
    }
}
