package dev.sift.fetch;

/**
 * 抓取 feed 時失敗。
 *
 * <p>與其他 exception 不同，這個帶著一個 {@link FailureType}——
 * 因為呼叫端唯一需要知道的事就是「這次失敗，明天還值不值得再試」。
 *
 * <p>分類的責任放在最靠近失敗現場的地方（{@code FetchClient}），
 * 因為只有那裡知道是 timeout 還是 404。
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
