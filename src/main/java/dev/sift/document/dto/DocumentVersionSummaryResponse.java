package dev.sift.document.dto;

import dev.sift.document.DocumentVersionSummary;

import java.time.Instant;

/**
 * 版本列表用的回應——不含內文。
 *
 * <p>為什麼不直接回傳 {@code DocumentVersionSummary}：
 * 那是 persistence 層的 projection interface。
 * 直接回傳等於把「資料庫怎麼查」變成 API 合約的一部分，
 * 之後改查詢方式就會連帶改變回應格式。
 *
 * <p>與 {@code DocumentSummary → DocumentSummaryResponse} 同一個模式。
 */
public record DocumentVersionSummaryResponse(
        Integer versionNumber,
        String title,
        Instant createdAt
) {

    public static DocumentVersionSummaryResponse from(DocumentVersionSummary summary) {
        return new DocumentVersionSummaryResponse(
                summary.getVersionNumber(),
                summary.getTitle(),
                summary.getCreatedAt()
        );
    }
}
