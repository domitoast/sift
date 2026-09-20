package dev.sift.document;

import java.time.Instant;

/**
 * Projection for the version list; excludes the body.
 */
public interface DocumentVersionSummary {
    Integer getVersionNumber();

    String getTitle();

    Instant getCreatedAt();
}
