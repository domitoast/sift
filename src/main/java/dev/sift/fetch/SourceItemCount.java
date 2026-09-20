package dev.sift.fetch;

/**
 * Per-source article counts, aggregated in one query rather than one per source.
 */
public interface SourceItemCount {
    Long getSourceId();

    long getTotal();

    long getReady();
}
