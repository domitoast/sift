package dev.sift.fetch.dto;

import dev.sift.fetch.FetchedItem;
import dev.sift.fetch.FetchedItemStatus;

import java.time.Instant;

/**
 * A fetched article with its full original content.
 */
public record FetchedItemDetailResponse(
        Long id,
        String title,
        FetchedItemStatus status,
        String summary,
        String rawContent,
        String externalUrl,
        Instant publishedAt,
        Instant promotedAt,
        String failureReason
) {
    public static FetchedItemDetailResponse from(FetchedItem item) {
        return new FetchedItemDetailResponse(
                item.getId(),
                item.getTitle(),
                item.getStatus(),
                item.getSummary(),
                item.getRawContent(),
                item.getExternalUrl(),
                item.getPublishedAt(),
                item.getPromotedAt(),
                item.getFailureReason()
        );
    }
}
