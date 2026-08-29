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
 * 一次抓取任務，對應資料表 {@code fetch_job}。
 *
 * <p>「排程觸發一次、對一個 source 抓一次」＝ 一筆紀錄。
 *
 * <p><b>沒有 setStatus()，是刻意的。</b>
 * 狀態只能透過 {@link #start()}、{@link #succeed()}、{@link #fail} 改變，
 * 每個方法自己檢查來源狀態合不合法，並同時更新該轉換必須更新的欄位。
 *
 * <p>這樣「換了狀態卻忘記記時間」就變成做不到的事，
 * 而不是「要記得做」的事。
 */
@Entity
@Table(name = "fetch_job")
public class FetchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 抓哪一個來源。只存 id，不建立 JPA 關聯（ADR-012）。 */
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FetchStatus status = FetchStatus.PENDING;

    /** 開始抓的時間。PENDING 期間是 null。 */
    @Column(name = "started_at")
    private Instant startedAt;

    /** 結束（成功或失敗）的時間。 */
    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", length = 20)
    private FailureType failureType;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    /** JPA 需要一個無參數建構子，但不希望外部呼叫，所以設 protected。 */
    protected FetchJob() {
    }

    public FetchJob(Long sourceId) {
        this.sourceId = sourceId;
        this.status = FetchStatus.PENDING;
    }

    /**
     * 開始抓取：PENDING → RUNNING。
     *
     * @throws IllegalFetchJobTransitionException 目前不是 PENDING
     */
    public void start() {
        requireStatus(FetchStatus.PENDING, FetchStatus.RUNNING);

        this.status = FetchStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * 抓取成功：RUNNING → SUCCESS。
     *
     * @throws IllegalFetchJobTransitionException 目前不是 RUNNING
     */
    public void succeed() {
        requireStatus(FetchStatus.RUNNING, FetchStatus.SUCCESS);

        this.status = FetchStatus.SUCCESS;
        this.finishedAt = Instant.now();
    }

    /**
     * 抓取失敗：RUNNING → FAILED。
     *
     * <p>失敗一定要說明原因——資料庫的 {@code ck_fetch_job_failure_reason}
     * 也強制了這件事。沒有原因的失敗紀錄沒有診斷價值。
     *
     * @throws IllegalFetchJobTransitionException 目前不是 RUNNING
     */
    public void fail(FailureType failureType, String reason) {
        requireStatus(FetchStatus.RUNNING, FetchStatus.FAILED);

        this.status = FetchStatus.FAILED;
        this.finishedAt = Instant.now();
        this.failureType = failureType;
        this.failureReason = reason;
    }

    /** 是否已經結束（不論成功或失敗）。 */
    public boolean isFinished() {
        return status == FetchStatus.SUCCESS || status == FetchStatus.FAILED;
    }

    private void requireStatus(FetchStatus required, FetchStatus target) {
        if (this.status != required) {
            throw new IllegalFetchJobTransitionException(this.status, target);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public FetchStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
