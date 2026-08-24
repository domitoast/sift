package dev.sift.document.dto;

import dev.sift.document.DocumentOrigin;
import dev.sift.document.DocumentSummary;

import java.time.Instant;

/**
 * 列表用的精簡版文件資料。
 *
 * <p><b>刻意不含 {@code content}。</b>
 * 20 篇 × 每篇 50 KB 的內文 = 1 MB 的回應，
 * 但畫面上的列表只需要標題與時間。要看全文請點進 {@code GET /documents/{id}}。
 *
 * <p><b>列表 API 的通則：列表給摘要，詳情給全文。</b>
 *
 * <p>⚠️ 若日後要在列表顯示「前 200 字預覽」，正確做法是在 SQL 端截斷
 * （例如 {@code LEFT(content, 200)} 的投影查詢），
 * 而不是把全文撈回 Java 再 {@code substring}——那樣完全沒省到傳輸量。
 */
public record DocumentSummaryResponse(
        Long id,
        String title,
        DocumentOrigin origin,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * 從 projection 轉換。
     *
     * <p>參數型別是 {@link DocumentSummary}（persistence 層的 projection）
     * 而不是 {@code Document}——因為列表查詢根本沒有載入完整的 entity。
     */
    public static DocumentSummaryResponse from(DocumentSummary summary) {
        return new DocumentSummaryResponse(
                summary.getId(),
                summary.getTitle(),
                summary.getOrigin(),
                summary.getCreatedAt(),
                summary.getUpdatedAt()
        );
    }
}
