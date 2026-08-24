package dev.sift.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * 對應資料表 {@code refresh_token}。
 *
 * <p>一列 = 一個 session（一個裝置的一次登入）。
 * 同一個使用者可以同時有多列——手機一列、筆電一列，互不影響。
 *
 * <p>採原地 rotation：換發時不新增列，而是更新這一列（ADR-011）。
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 擁有者的 id。
     *
     * <p><b>刻意只存 id，不做 {@code @ManyToOne} 關聯到 User。</b>
     *
     * <p>理由：驗證 refresh token 時我們只需要知道「這是誰的」，
     * 不需要 email、密碼雜湊等任何使用者資料。
     * 建立關聯的話，Hibernate 每次載入這一列都可能連帶去查 app_user，
     * 那是白花的一次查詢。
     *
     * <p>（JPA 關聯與它的 N+1 問題是 Day 9 的主題。這裡刻意不用。）
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 目前有效的 token 的 SHA-256 雜湊值（64 個十六進位字元）。
     *
     * <p><b>資料庫裡存的是雜湊，不是 token 本身。</b>
     * 理由與密碼相同：資料庫外洩時，攻擊者拿到雜湊值無法反推出可用的 token。
     *
     * <p><b>為什麼用 SHA-256 而不是 BCrypt</b>：
     * 密碼是人想出來的，可能是「password123」，所以要用慢的演算法拖住暴力破解。
     * 但 refresh token 是 {@code SecureRandom} 產生的 256 bits 隨機值——
     * 猜中的機率等同於猜中一組 256 位元的亂數，本來就不可行，不需要刻意放慢。
     *
     * <p>用 BCrypt 只會讓每次 refresh 多等 100 毫秒，換不到任何安全性。
     *
     * <p><b>原則：雜湊演算法的選擇取決於「被保護的值有多容易猜」。</b>
     */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /**
     * 上一代 token 的雜湊值。首次登入時為 null，第一次換發後才有值。
     *
     * <p>存在的唯一理由是<b>盜用偵測</b>：
     * 若有人拿著與這個欄位相符的 token 來換發，代表一張已經被換掉的票
     * 又出現了——正常流程下不可能發生，因此判定為盜用（ADR-011）。
     */
    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    /**
     * 絕對到期時間。
     *
     * <p><b>rotation 不會延長它</b>（ADR-011）。
     * 七天是「這個 session 的壽命上限」，不是「閒置多久才過期」。
     * 因此使用者即使天天在用，每七天仍需重新登入一次。
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * 撤銷時間。null 表示尚未撤銷。
     *
     * <p>三種情況會被填上：登出、偵測到盜用、使用者主動登出所有裝置。
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    /** JPA 規範要求的無參數建構子。宣告為 protected，避免一般程式碼建立空物件。 */
    protected RefreshToken() {
    }

    public RefreshToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * 換發：把目前的雜湊移到 previous，寫入新的雜湊。
     *
     * <p><b>expiresAt 刻意不動。</b>
     *
     * <p>把這個動作封裝在 Entity 裡，而不是讓 Service 寫
     * {@code setPreviousTokenHash(...)} 加 {@code setTokenHash(...)}：
     * 前者只有一種正確用法，後者可以被用錯順序（先設新的再設舊的，
     * 結果兩個欄位變成同一個值，偵測邏輯就永遠不會觸發）。
     */
    public void rotate(String newTokenHash) {
        this.previousTokenHash = this.tokenHash;
        this.tokenHash = newTokenHash;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /** 是否還能用來換發。三個條件必須同時成立。 */
    public boolean isUsable() {
        return !isRevoked() && !isExpired();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getPreviousTokenHash() {
        return previousTokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /*
     * ⚠️ 刻意不覆寫 toString()。
     *
     * 雖然這裡存的已經是雜湊而非 token 本身，
     * 但雜湊值仍是查詢資料庫的鍵——印進日誌沒有任何好處。
     */
}
