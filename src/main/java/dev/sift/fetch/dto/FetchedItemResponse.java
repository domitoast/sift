package dev.sift.fetch.dto;

import dev.sift.fetch.FetchedItem;
import dev.sift.fetch.FetchedItemStatus;

import java.time.Instant;

/**
 * A fetched article without its original content, for list views.
 */
public record FetchedItemResponse(
        Long id,
        String title,
        String summary,
        FetchedItemStatus status,
        String externalUrl,
        Instant publishedAt,
        String failureReason
) {
    public static FetchedItemResponse from(FetchedItem item) {
        return new FetchedItemResponse(
                item.getId(),
                item.getTitle(),
                item.getSummary(),
                item.getStatus(),
                item.getExternalUrl(),
                item.getPublishedAt(),
                item.getFailureReason()
        );
    }
}
