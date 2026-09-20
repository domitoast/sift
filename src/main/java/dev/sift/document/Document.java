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
 * An entry in the knowledge base, either promoted from a fetched article
 * or written by hand. Uses optimistic locking to detect concurrent edits.
 */
@Entity
@Table(name = "document")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 10)
    private DocumentOrigin origin;

    @Column(name = "fetched_item_id")
    private Long fetchedItemId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

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

    protected Document() {
    }

    public Document(Long userId, String title, String content) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.origin = DocumentOrigin.MANUAL;
    }

    public static Document fromFetchedItem(Long userId, Long fetchedItemId,
                                           String title, String content) {
        Document document = new Document();
        document.userId = userId;
        document.fetchedItemId = fetchedItemId;
        document.title = title;
        document.content = content;
        document.origin = DocumentOrigin.FETCHED;
        return document;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

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
}
