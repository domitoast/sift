package dev.sift.fetch;

/**
 * 試圖對一篇 {@link FetchedItem} 做不合法的狀態轉換。
 *
 * <p>與 {@link IllegalFetchJobTransitionException} 同樣沒有對應的 handler，
 * 理由也一樣：這代表程式有 bug，不是使用者做錯事。
 * 讓它變成 500 是正確的。
 */
public class IllegalFetchedItemTransitionException extends RuntimeException {

    public IllegalFetchedItemTransitionException(FetchedItemStatus from, FetchedItemStatus to) {
        super("文章狀態不能從 %s 轉換到 %s".formatted(from, to));
    }
}
