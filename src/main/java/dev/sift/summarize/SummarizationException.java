package dev.sift.summarize;

import dev.sift.fetch.FailureType;

/**
 * 產生摘要失敗。
 *
 * <p>與 {@code FeedFetchException} 同樣帶著 {@link FailureType}——
 * 呼叫端唯一需要知道的是「這次失敗，等一下還值不值得再試」。
 *
 * <p>Day 19 的 retry 與 exponential backoff 就是依這個分類決定要不要重試：
 *
 * <table border="1">
 *   <tr><td>{@code TRANSIENT}</td><td>429 限流、timeout、對方 5xx → <b>值得重試</b></td></tr>
 *   <tr><td>{@code PERMANENT}</td><td>API key 無效、內容為空 → 重試幾次都一樣</td></tr>
 * </table>
 *
 * <p>⚠️ 訊息裡絕對不可以包含 API key。
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
