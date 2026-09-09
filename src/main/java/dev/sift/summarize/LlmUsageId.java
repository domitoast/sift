package dev.sift.summarize;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * {@link LlmUsage} 的複合主鍵：一個使用者一天一列。
 *
 * <p><b>為什麼需要一個獨立的類別</b>：JPA 的 {@code @Id} 只能標一個欄位。
 * 主鍵由兩個欄位組成時，必須把它們包成一個型別。
 *
 * <p>用 record 而不是一般類別：主鍵建立後就不該改變，
 * 而且 JPA 要求主鍵類別要正確實作 {@code equals} 與 {@code hashCode}——
 * record 自動幫你生好。
 *
 * <p>{@code Serializable} 是 JPA 規格對主鍵類別的要求。
 */
public record LlmUsageId(Long userId, LocalDate usageDate) implements Serializable {
}
