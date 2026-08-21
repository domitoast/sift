package dev.sift.user;

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
 * 對應資料表 {@code app_user}。
 *
 * <p>一個 User 實例 = 資料表中的一列。
 *
 * <p>設計說明見 docs/DATABASE_DESIGN.md 第 1 節。
 */
@Entity
// 類別叫 User，資料表叫 app_user，名字不同所以要明講。
// （app_user 的命名理由：user 是 PostgreSQL 保留字）
@Table(name = "app_user")
public class User {

    /**
     * 主鍵。
     *
     * <p>{@code GenerationType.IDENTITY} 表示「由資料庫產生」——
     * 對應到 PostgreSQL 的 BIGSERIAL。
     *
     * <p>其他選項：
     * <ul>
     *   <li>{@code SEQUENCE} — 使用資料庫序列，可批次取號，寫入量大時效能較好</li>
     *   <li>{@code AUTO} — 讓 Hibernate 自己決定，行為不可預測，不建議</li>
     * </ul>
     *
     * <p>本專案寫入量小，IDENTITY 足夠且最直觀。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    /**
     * BCrypt 雜湊值，長度固定 60 字元。
     *
     * <p>⚠️ 這裡存的**永遠**是雜湊值，不是明文密碼。
     * 欄位命名刻意叫 passwordHash 而非 password，就是為了讓
     * 「不小心存進明文」這件事在閱讀程式碼時顯得刺眼。
     */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    /**
     * 加密後的 LLM API key（ADR-003 BYOK）。未設定時為 null。
     */
    @Column(name = "llm_api_key_encrypted")
    private String llmApiKeyEncrypted;

    /**
     * 建立時間。
     *
     * <p>{@code insertable = false} 表示 Hibernate 產生 INSERT 時
     * <b>不包含這個欄位</b>，交給資料庫的 {@code DEFAULT now()} 決定。
     *
     * <p>{@code updatable = false} 表示 UPDATE 時也不碰它。
     *
     * <p>{@code @Generated(event = INSERT)} 則告訴 Hibernate：
     * 「這個值由資料庫產生，寫入後請幫我讀回來」。
     * 少了它，INSERT 之後記憶體中的 createdAt 會是 null，
     * 直到下次重新查詢才有值——這是很常見的困惑來源。
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    /**
     * 最後更新時間，由資料庫 trigger {@code set_updated_at()} 維護。
     *
     * <p>同樣不由 Hibernate 寫入，但 INSERT 與 UPDATE 後都要讀回來。
     */
    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    /**
     * soft delete 標記（ADR-005）。null 表示未刪除。
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * JPA 規範要求每個 Entity 必須有一個無參數建構子，
     * 因為它在從資料庫讀取資料時會先建立空物件，再逐一填入欄位。
     *
     * <p>宣告為 {@code protected} 而非 {@code public}：
     * JPA 用得到，但一般程式碼不該建立「什麼都沒有的 User」。
     * 這是在不違反框架要求的前提下，盡量縮小濫用的可能。
     */
    protected User() {
    }

    /**
     * 建立新使用者用的建構子。
     *
     * <p>只接收「建立時真正需要的兩個值」。
     * id 由資料庫產生、時間戳由資料庫維護，都不該由呼叫者提供。
     *
     * @param email        登入帳號
     * @param passwordHash <b>已經雜湊過</b>的密碼，絕非明文
     */
    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getLlmApiKeyEncrypted() {
        return llmApiKeyEncrypted;
    }

    public void setLlmApiKeyEncrypted(String llmApiKeyEncrypted) {
        this.llmApiKeyEncrypted = llmApiKeyEncrypted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 執行 soft delete。
     *
     * <p>把「怎麼刪除」封裝在 Entity 內部，而不是讓 Service 寫
     * {@code user.setDeletedAt(Instant.now())}。
     *
     * <p>差別在於：前者只有一種正確用法，後者可以被傳入任意時間
     * （包括未來時間、null），行為不可預測。
     */
    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    /**
     * ⚠️ 刻意不覆寫 {@code toString()}。
     *
     * <p>若覆寫並印出所有欄位，passwordHash 與加密後的 API key
     * 就可能隨著日誌外流（違反 NFR-3.1、NFR-3.7）。
     *
     * <p>需要在日誌中識別使用者時，只印 id 或 email。
     */

    /**
     * ⚠️ 刻意不覆寫 {@code equals()} 與 {@code hashCode()}。
     *
     * <p>JPA Entity 的 equals/hashCode 有著名的陷阱：
     * 若以 id 為基準，物件在被存入資料庫「之前」id 是 null，
     * 兩個不同的新使用者會被判定相等。
     *
     * <p>目前的程式碼不需要把 User 放進 Set 或當成 Map 的 key，
     * 因此保留 Java 預設的物件識別即可。
     * Day 8 處理實體關聯時會再回頭討論這個問題。
     */
}
