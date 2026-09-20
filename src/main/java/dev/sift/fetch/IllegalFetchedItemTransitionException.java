package dev.sift.fetch;

/**
 * An illegal article state transition, e.g. promoting something already discarded.
 */
public class IllegalFetchedItemTransitionException extends RuntimeException {
    public IllegalFetchedItemTransitionException(FetchedItemStatus from, FetchedItemStatus to) {
        super("文章狀態不能從 %s 轉換到 %s".formatted(from, to));
    }
}
