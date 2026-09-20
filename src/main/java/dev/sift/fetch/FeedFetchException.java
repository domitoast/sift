package dev.sift.fetch;

/**
 * The feed could not be downloaded: timeout, connection refused, 404, 5xx.
 */
public class FeedFetchException extends RuntimeException {
    private final FailureType failureType;

    public FeedFetchException(FailureType failureType, String reason) {
        super(reason);
        this.failureType = failureType;
    }

    public FailureType getFailureType() {
        return failureType;
    }
}
