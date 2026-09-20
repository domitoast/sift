package dev.sift.document.dto;

import dev.sift.document.DocumentVersionSummary;

import java.time.Instant;

/**
 * One historical version, without its body.
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
