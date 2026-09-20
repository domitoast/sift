package dev.sift.fetch;

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
 * A stored article, as a state machine:
 * NEW -> SUMMARIZING -> READY -> PROMOTED | DISCARDED, with FAILED for retries.
 *
 * As with FetchJob there is no status setter; each transition also clears the
 * fields that no longer apply, so stale failure reasons cannot survive a success.
 */
@Entity
@Table(name = "fetched_item")
public class FetchedItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "fetch_job_id", nullable = false)
    private Long fetchJobId;

    @Column(name = "external_url", nullable = false, length = 1000)
    private String externalUrl;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "raw_content")
    private String rawContent;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FetchedItemStatus status = FetchedItemStatus.NEW;

    @Column(name = "summary")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", length = 20)
    private FailureType failureType;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    protected FetchedItem() {
    }

    public FetchedItem(Long sourceId, Long fetchJobId, String contentHash,
                       FetchedArticle article) {
        this.sourceId = sourceId;
        this.fetchJobId = fetchJobId;
        this.contentHash = contentHash;
        this.externalUrl = article.link();
        this.title = truncate(article.title(), 500);
        this.rawContent = article.content();
        this.publishedAt = article.publishedAt();
        this.status = FetchedItemStatus.NEW;
    }

    public void startSummarizing() {
        requireStatus(FetchedItemStatus.NEW, FetchedItemStatus.SUMMARIZING);

        this.status = FetchedItemStatus.SUMMARIZING;
    }

    public void summarized(String summary) {
        requireStatus(FetchedItemStatus.SUMMARIZING, FetchedItemStatus.READY);

        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("摘要不可為空——READY 狀態必須有摘要");
        }

        this.summary = summary.trim();
        this.status = FetchedItemStatus.READY;

        this.failureType = null;
        this.failureReason = null;
    }

    public void failSummarization(FailureType failureType, String reason) {
        requireStatus(FetchedItemStatus.SUMMARIZING, FetchedItemStatus.FAILED);

        this.status = FetchedItemStatus.FAILED;
        this.failureType = failureType;
        this.failureReason = reason;
    }

    public void retryLater(String reason, Instant nextRetryAt) {
        requireStatus(FetchedItemStatus.SUMMARIZING, FetchedItemStatus.NEW);

        this.status = FetchedItemStatus.NEW;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.failureType = FailureType.TRANSIENT;
        this.failureReason = reason;
    }

    public void promote() {
        requireStatus(FetchedItemStatus.READY, FetchedItemStatus.PROMOTED);

        this.status = FetchedItemStatus.PROMOTED;
        this.promotedAt = Instant.now();
    }

    public void discard() {
        requireStatus(FetchedItemStatus.READY, FetchedItemStatus.DISCARDED);

        this.status = FetchedItemStatus.DISCARDED;
    }

    public void resummarize() {
        if (this.status != FetchedItemStatus.READY && this.status != FetchedItemStatus.FAILED) {
            throw new IllegalFetchedItemTransitionException(this.status, FetchedItemStatus.NEW);
        }

        this.status = FetchedItemStatus.NEW;
        this.summary = null;
        this.retryCount = 0;
        this.nextRetryAt = null;
        this.failureType = null;
        this.failureReason = null;
    }

    private void requireStatus(FetchedItemStatus required, FetchedItemStatus target) {
        if (this.status != required) {
            throw new IllegalFetchedItemTransitionException(this.status, target);
        }
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getFetchJobId() {
        return fetchJobId;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getTitle() {
        return title;
    }

    public String getRawContent() {
        return rawContent;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public FetchedItemStatus getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getPromotedAt() {
        return promotedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
