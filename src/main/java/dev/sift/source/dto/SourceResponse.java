package dev.sift.source.dto;

import dev.sift.source.Source;
import dev.sift.source.SourceType;

import java.time.Instant;

/**
 * 對外回傳的訂閱來源。
 */
public record SourceResponse(
        Long id,
        String name,
        String url,
        SourceType type,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    public static SourceResponse from(Source source) {
        return new SourceResponse(
                source.getId(),
                source.getName(),
                source.getUrl(),
                source.getType(),
                source.isEnabled(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
