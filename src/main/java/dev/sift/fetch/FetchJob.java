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
 * One fetch attempt, as a state machine: PENDING -> RUNNING -> SUCCESS | FAILED.
 *
 * There is no status setter on purpose. Each transition method validates the
 * current state and updates every field that transition implies, so "changed the
 * status but forgot the timestamp" is not expressible.
 */
@Entity
@Table(name = "fetch_job")
public class FetchJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FetchStatus status = FetchStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", length = 20)
    private FailureType failureType;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "new_item_count", nullable = false)
    private int newItemCount = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    protected FetchJob() {
    }

    public FetchJob(Long sourceId) {
        this.sourceId = sourceId;
        this.status = FetchStatus.PENDING;
    }

    public void start() {
        requireStatus(FetchStatus.PENDING, FetchStatus.RUNNING);

        this.status = FetchStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void succeed(int newItemCount) {
        requireStatus(FetchStatus.RUNNING, FetchStatus.SUCCESS);

        this.status = FetchStatus.SUCCESS;
        this.finishedAt = Instant.now();
        this.newItemCount = newItemCount;
    }

    public void fail(FailureType failureType, String reason) {
        if (isFinished()) {
            throw new IllegalFetchJobTransitionException(this.status, FetchStatus.FAILED);
        }

        this.status = FetchStatus.FAILED;
        this.finishedAt = Instant.now();
        this.failureType = failureType;
        this.failureReason = reason;
    }

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

    public int getNewItemCount() {
        return newItemCount;
    }
}
