package dev.sift.fetch;

/**
 * This source already has a job queued or running.
 */
public class FetchAlreadyRunningException extends RuntimeException {
    public FetchAlreadyRunningException() {
        super("這個來源正在抓取中，請稍候再試");
    }
}
