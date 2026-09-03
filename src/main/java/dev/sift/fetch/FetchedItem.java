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
 * 一篇抓下來的文章，對應資料表 {@code fetched_item}。
 *
 * <p>這是「暫存區」——文章抓下來先放在這裡，等使用者決定要不要收進知識庫
 * （ADR-002：staging vs curated）。
 *
 * <p><b>身分由 {@code (source_id, content_hash)} 決定</b>（ADR-004），
 * 不是網址。同一篇文章的網址每天可能帶不同的追蹤參數，
 * 但內容不變，雜湊值就不變。
 *
 * <p>Day 17 只會用到「抓下來、標成 NEW」這一段。
 * {@code summary}、{@code promoted_at}、{@code retry_count} 等欄位
 * 資料庫裡已經有了，但還沒有對應到 Java——Day 18 才會用到。
 */
@Entity
@Table(name = "fetched_item")
public class FetchedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 哪個來源抓來的。只存 id，不建立 JPA 關聯（ADR-012）。 */
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    /** 哪一次抓取抓到的。用來追查「這篇是什麼時候進來的」。 */
    @Column(name = "fetch_job_id", nullable = false)
    private Long fetchJobId;

    @Column(name = "external_url", nullable = false, length = 1000)
    private String externalUrl;

    /**
     * dedup 的依據（ADR-004）。SHA-256 轉十六進位，固定 64 字元。
     *
     * <p>與 {@code source_id} 一起構成資料庫的唯一約束
     * {@code uq_fetched_item_source_hash}。
     */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /** 原始內文。可能是 null——有些 feed 只給標題和連結。 */
    @Column(name = "raw_content")
    private String rawContent;

    /** feed 上寫的發布時間。很多 feed 不填，所以可能是 null。 */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FetchedItemStatus status = FetchedItemStatus.NEW;

    /**
     * LLM 產生的摘要。
     *
     * <p>資料庫的 {@code ck_fetched_item_summary} 約束要求：
     * 狀態是 READY 或 PROMOTED 時，這裡不可以是 null。
     */
    @Column(name = "summary")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", length = 20)
    private FailureType failureType;

    @Column(name = "failure_reason")
    private String failureReason;

    /** 已經試過幾次。第一次處理時是 0。 */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /**
     * 下次可以重試的時間。null 代表「隨時可以處理」。
     *
     * <p><b>存絕對時間而不是「還要等幾秒」</b>——後者需要有人一直在數，
     * 服務重啟就忘了。這個決定寫在 V1 的欄位註解裡（Day 3）。
     */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

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

    /**
     * 開始產生摘要：NEW → SUMMARIZING。
     *
     * <p><b>為什麼需要這個中間狀態</b>：呼叫 LLM 可能要好幾秒。
     * 如果程式在那期間掛掉，這一筆會停在 SUMMARIZING——
     * 你就知道「它開始過但沒完成」，而不是「它從來沒被處理過」。
     *
     * <p>兩者的處置不同：前者可能已經付過錢了。
     *
     * @throws IllegalFetchedItemTransitionException 目前不是 NEW
     */
    public void startSummarizing() {
        requireStatus(FetchedItemStatus.NEW, FetchedItemStatus.SUMMARIZING);

        this.status = FetchedItemStatus.SUMMARIZING;
    }

    /**
     * 摘要完成：SUMMARIZING → READY。
     *
     * <p>摘要和狀態一起更新，所以不會出現「狀態說好了但摘要是空的」。
     * 資料庫的 {@code ck_fetched_item_summary} 是同一件事的第二道防線。
     *
     * @throws IllegalFetchedItemTransitionException 目前不是 SUMMARIZING
     */
    public void summarized(String summary) {
        requireStatus(FetchedItemStatus.SUMMARIZING, FetchedItemStatus.READY);

        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("摘要不可為空——READY 狀態必須有摘要");
        }

        this.summary = summary.trim();
        this.status = FetchedItemStatus.READY;
    }

    /**
     * 摘要失敗：SUMMARIZING → FAILED。
     *
     * @throws IllegalFetchedItemTransitionException 目前不是 SUMMARIZING
     */
    public void failSummarization(FailureType failureType, String reason) {
        requireStatus(FetchedItemStatus.SUMMARIZING, FetchedItemStatus.FAILED);

        this.status = FetchedItemStatus.FAILED;
        this.failureType = failureType;
        this.failureReason = reason;
    }

    /**
     * 暫時性失敗，稍後再試：SUMMARIZING → NEW。
     *
     * <p><b>為什麼是回到 NEW 而不是 FAILED</b>：
     * {@code FAILED} 的語意是「這件事結束了」。但暫時性失敗還沒結束——
     * 它只是還沒成功。回到 {@code NEW} 就是「重新排隊」。
     *
     * <p>不會馬上被撿走，因為 {@code nextRetryAt} 是未來的時間，
     * 查詢會把它排除掉。
     *
     * <p>失敗原因仍然記下來——排隊中的項目也應該看得出「上次為什麼失敗」。
     *
     * @param nextRetryAt 下次可以重試的時間，由 {@code RetryPolicy} 算出
     * @throws IllegalFetchedItemTransitionException 目前不是 SUMMARIZING
     */
    public void retryLater(String reason, Instant nextRetryAt) {
        requireStatus(FetchedItemStatus.SUMMARIZING, FetchedItemStatus.NEW);

        this.status = FetchedItemStatus.NEW;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.failureType = FailureType.TRANSIENT;
        this.failureReason = reason;
    }

    private void requireStatus(FetchedItemStatus required, FetchedItemStatus target) {
        if (this.status != required) {
            throw new IllegalFetchedItemTransitionException(this.status, target);
        }
    }

    /**
     * 標題超過欄位長度時截斷。
     *
     * <p><b>為什麼要自己截而不是讓資料庫報錯</b>：
     * 標題是別人的網站給的，我們控制不了。
     * 某個網站放一個 600 字的標題，不應該讓整批抓取失敗。
     *
     * <p>⚠️ 注意這裡截的是<b>標題</b>，不是計算雜湊值用的那份。
     * 雜湊用的是完整的原始標題，所以截斷不會影響 dedup 的正確性。
     */
    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
