package dev.sift.fetch;

/**
 * An illegal fetch job state transition, which always means a bug rather than
 * bad user input. Intentionally has no handler, so it surfaces as a 500.
 */
public class IllegalFetchJobTransitionException extends RuntimeException {
    public IllegalFetchJobTransitionException(FetchStatus from, FetchStatus to) {
        super("抓取任務不能從 %s 轉換到 %s".formatted(from, to));
    }
}
