package dev.sift.fetch;

/**
 * Lifecycle of a fetched article.
 */
public enum FetchedItemStatus {
    NEW,

    SUMMARIZING,

    READY,

    PROMOTED,

    FAILED,

    DISCARDED
}
