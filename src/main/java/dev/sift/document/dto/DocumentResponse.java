package dev.sift.document.dto;

import dev.sift.document.Document;
import dev.sift.document.DocumentOrigin;

import java.time.Instant;

/**
 * A document with its body.
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
