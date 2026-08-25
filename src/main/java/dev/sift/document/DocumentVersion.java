package dev.sift.document;

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
 * 文件在某個時間點的完整快照。
 *
 * <p><b>存的是完整內容，不是差異。</b>
 * 第 3 版存整篇「1. 預算 / 2. 時程 / 3. 人力」，不是「加了 3. 人力」。
 *
 * <p>存差異（git 的做法）能省空間，但讀第 15 版要從第 1 版開始套用 14 次。
 * 以本專案的規模（一版 2–5 KB、最多 20 版）不值得換那個複雜度。
 *
 * <p><b>{@code title} 也要存</b>：標題本身會被改，
 * 只存內文的話回頭看舊版會配到現在的標題。
 *
 * <p>沒有 {@code user_id}：擁有者由 {@code document_id} 指向的那篇文件決定，
 * 因此權限檢查在 document 那一層做（先確認文件是你的，才查它的版本）。
 */
@Entity
@Table(name = "document_version")
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 第幾版，從 1 開始。資料庫有 CHECK 約束擋住小於 1 的值。 */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    protected DocumentVersion() {
    }

    /**
     * 從一份文件的「當下狀態」建立快照。
     *
     * <p>用 Document 當參數而不是拆成三個字串，是為了讓呼叫端不可能
     * 把 A 文件的標題配上 B 文件的內文。
     */
    public static DocumentVersion snapshotOf(Document document, int versionNumber) {
        DocumentVersion version = new DocumentVersion();
        version.documentId = document.getId();
        version.versionNumber = versionNumber;
        version.title = document.getTitle();
        version.content = document.getContent();
        return version;
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
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
}
