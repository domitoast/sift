package dev.sift.summarize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Per-user, per-day call count, used to enforce the daily quota.
 */
@Entity
@Table(name = "llm_usage")
@IdClass(LlmUsageId.class)
public class LlmUsage {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    protected LlmUsage() {
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public int getCallCount() {
        return callCount;
    }
}
