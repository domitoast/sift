package dev.sift.summarize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * 一個使用者某一天的 LLM 呼叫次數。
 *
 * <p><b>這個 entity 幾乎不會被 Java 這一側操作</b>——
 * 讀寫都走 {@link LlmUsageRepository} 的原生 SQL（因為要用 UPSERT 和
 * {@code CURRENT_DATE}）。
 *
 * <p>它存在的理由是讓 {@code ddl-auto: validate} 能檢查
 * Java 與資料表對得起來，以及日後若需要一般查詢時有東西可用。
 *
 * <p>{@code @IdClass} 告訴 JPA「主鍵由多個欄位組成，長相定義在那個類別裡」。
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
