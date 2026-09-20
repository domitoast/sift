package dev.sift.source.dto;

import dev.sift.source.Source;
import dev.sift.source.SourceType;

import java.time.Instant;

/**
 * A subscription with its article counts. fetchJobId is only set on creation.
 */
public record SourceResponse(
        Long id,
        String name,
        String url,
        SourceType type,
        boolean enabled,
        long itemCount,
        long readyCount,
        Long fetchJobId,
        Instant createdAt,
        Instant updatedAt
) {
    public static SourceResponse from(Source source) {
        return from(source, 0, 0, null);
    }

    public static SourceResponse from(Source source, Long fetchJobId) {
        return from(source, 0, 0, fetchJobId);
    }

    public static SourceResponse from(Source source, long itemCount, long readyCount) {
        return from(source, itemCount, readyCount, null);
    }

    public static SourceResponse from(Source source,
                                      long itemCount,
                                      long readyCount,
                                      Long fetchJobId) {
        return new SourceResponse(
                source.getId(),
                source.getName(),
                source.getUrl(),
                source.getType(),
                source.isEnabled(),
                itemCount,
                readyCount,
                fetchJobId,
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
