package dev.sift.document;

import java.time.Instant;

/**
 * Projection for list views; deliberately excludes the body.
 */
public interface DocumentSummary {
    Long getId();

    String getTitle();

    DocumentOrigin getOrigin();

    Instant getCreatedAt();

    Instant getUpdatedAt();
}
