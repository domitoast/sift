package dev.sift.document.dto;

import dev.sift.document.DocumentVersion;

import java.time.Instant;

/**
 * 單一版本的完整內容。
 *
 * <p>不含 {@code documentId}：呼叫端是打 {@code /documents/5/versions/2} 進來的，
 * 他已經知道是哪篇文件了。回傳一個他已知的值沒有意義。
 */
public record DocumentVersionResponse(
        Integer versionNumber,
        String title,
        String content,
        Instant createdAt
) {

    public static DocumentVersionResponse from(DocumentVersion version) {
        return new DocumentVersionResponse(
                version.getVersionNumber(),
                version.getTitle(),
                version.getContent(),
                version.getCreatedAt()
        );
    }
}
