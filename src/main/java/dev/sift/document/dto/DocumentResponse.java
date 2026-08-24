package dev.sift.document.dto;

import dev.sift.document.Document;
import dev.sift.document.DocumentOrigin;

import java.time.Instant;

/**
 * 對外回傳的單篇文件。
 *
 * <p>刻意不包含 {@code userId}：呼叫端拿到的一定是自己的文件，
 * 回傳一個他已經知道的值沒有意義，只是多洩漏一個內部識別碼。
 *
 * <p>也不包含 {@code deletedAt}——已刪除的文件根本查不到。
 */
public record DocumentResponse(
        Long id,
        String title,
        String content,
        DocumentOrigin origin,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * {@code version} 必須回傳給呼叫端。
     *
     * <p>因為編輯時它要把「讀到的 version」送回來，我們才能判斷
     * 這中間有沒有別人改過。不回傳的話，前端沒有東西可以送。
     */
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getContent(),
                document.getOrigin(),
                document.getVersion(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
