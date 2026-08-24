package dev.sift.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * 對應資料表 {@code document}。一個實例 = 一列。
 */
@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 擁有者。與 RefreshToken 相同，只存 id 不建立 JPA 關聯——
     * 本專案沒有任何一支 API 需要從 Document 取得 User 的資料。
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * {@code EnumType.STRING} 讓資料庫存 "MANUAL" 這樣的字串。
     *
     * <p><b>⚠️ 預設值是 ORDINAL（存 0、1 這種順序編號），絕對不要用。</b>
     * 那代表哪天有人在 enum 中間插入一個新值，所有既有資料的意義就全部錯位，
     * 而且不會有任何錯誤訊息。
     *
     * <p>存字串多花幾個 byte，換到的是「資料自己看得懂」與「順序可以隨便改」。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 10)
    private DocumentOrigin origin;

    /** 由哪筆 fetched_item promote 而來。MANUAL 時為 null。 */
    @Column(name = "fetched_item_id")
    private Long fetchedItemId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /**
     * {@code columnDefinition = "TEXT"} 是必要的。
     *
     * <p>Hibernate 看到 String 預設會對應 {@code VARCHAR(255)}，
     * 但資料庫這一欄是 {@code TEXT}（無長度上限）。
     * 不指定的話，啟動時 {@code ddl-auto: validate} 會擋下來。
     *
     * <p>代價：這一行寫死了資料庫的型別名稱。可接受，因為 ADR-007
     * 已經決定綁定 PostgreSQL。
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * optimistic lock 用的版本號。
     *
     * <p>{@code @Version} 讓 Hibernate 自動做兩件事：
     * <ol>
     *   <li>每次 UPDATE 時把它 +1</li>
     *   <li>在 UPDATE 的 WHERE 加上 {@code AND version = ?}（載入時讀到的值）</li>
     * </ol>
     *
     * <p>若那句 UPDATE 影響 0 列，代表有人在這中間改過，
     * Hibernate 會拋出 {@code OptimisticLockingFailureException}。
     *
     * <p><b>⚠️ 它只擋得住「同時進行的兩個請求」。</b>
     * 擋不住「使用者五分鐘前讀取、現在才送出」——
     * 因為每個 HTTP 請求都會重新載入，載到的一定是最新的 version。
     * 那種情況要靠 Service 明確比對前端送回來的 version。
     *
     * <p>這個欄位<b>與 {@code document_version} 表完全無關</b>：
     * 這裡是併發控制（使用者看不到用途），那裡是使用者可見的歷史快照。
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /*
     * 資料表還有一個 version 欄位，今天刻意「不」對應。
     *
     * 它是給 optimistic lock 用的（@Version），Day 10 處理編輯功能時才加。
     * 現在沒有編輯功能，加了也沒有作用，只會多一個看不懂的標註。
     *
     * 資料庫端有 DEFAULT 0，所以不對應也不影響寫入。
     */

    protected Document() {
    }

    /**
     * 建立使用者手動輸入的文件。
     *
     * <p>只開放 MANUAL 這一種建構方式。FETCHED 的文件必須帶 fetchedItemId，
     * 且有 CHECK 約束要求兩者一致——那條路徑等 Day 11 有管線時再開。
     */
    public Document(Long userId, String title, String content) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.origin = DocumentOrigin.MANUAL;
    }

    /** 更新標題與內文。{@code updated_at} 由資料庫的 trigger 維護。 */
    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /** soft delete。把「怎麼刪」封裝起來，避免呼叫端傳入奇怪的時間。 */
    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Long getUserId() {
        return userId;
    }

    public DocumentOrigin getOrigin() {
        return origin;
    }

    public Long getFetchedItemId() {
        return fetchedItemId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
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

    /*
     * 同 User 與 RefreshToken：刻意不覆寫 toString / equals / hashCode。
     * content 可能很長，印進日誌沒有意義。
     */
}
