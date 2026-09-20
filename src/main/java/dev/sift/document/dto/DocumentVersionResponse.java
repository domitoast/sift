package dev.sift.document.dto;

import dev.sift.document.DocumentVersion;

import java.time.Instant;

/**
 * One historical version, with its body.
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
