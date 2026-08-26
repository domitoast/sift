package dev.sift.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * 一個訂閱來源，對應資料表 {@code source}。
 *
 * <p>使用者訂閱的 RSS / Atom 網址。排程會定期從這裡抓取新文章。
 */
@Entity
@Table(name = "source")
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 擁有者。同 Document，只存 id 不建立 JPA 關聯（ADR-012）。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 使用者自己取的名稱，例如「Hacker News」。 */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SourceType type;

    /**
     * 是否啟用。停用的來源排程會跳過，但資料保留。
     *
     * <p>與 soft delete 的差別：停用是「暫時不抓」，刪除是「不要了」。
     * 使用者可能只是暫時不想看某個來源。
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Source() {
    }

    public Source(Long userId, String name, String url, SourceType type) {
        this.userId = userId;
        this.name = name;
        this.url = url;
        this.type = type;
        this.enabled = true;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public SourceType getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
