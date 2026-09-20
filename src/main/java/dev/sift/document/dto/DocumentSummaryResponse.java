package dev.sift.document.dto;

import dev.sift.document.DocumentOrigin;
import dev.sift.document.DocumentSummary;

import java.time.Instant;

/**
 * A document without its body, for list views.
 */
public record DocumentSummaryResponse(
        Long id,
        String title,
        DocumentOrigin origin,
        Instant createdAt,
        Instant updatedAt
) {
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
