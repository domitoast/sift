package dev.sift.summarize;

import dev.sift.fetch.FailureType;

/**
 * Summarization failed, carrying whether a retry is worthwhile.
 */
public class SummarizationException extends RuntimeException {
    private final FailureType failureType;

    public SummarizationException(FailureType failureType, String reason) {
        super(reason);
        this.failureType = failureType;
    }

    public FailureType getFailureType() {
        return failureType;
    }
}
