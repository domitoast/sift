package dev.sift.fetch;

/**
 * 試圖做一個不合法的狀態轉換，例如把已經 SUCCESS 的任務重新啟動。
 *
 * <p><b>這個 exception 沒有對應的 handler，是刻意的。</b>
 *
 * <p>fetch_job 不對外開 API，只有排程會操作它。
 * 因此走到這裡代表<b>程式有 bug</b>，不是使用者做錯事。
 * 讓它落到 catch-all 變成 500，是正確的——
 * 500 的意思就是「伺服器自己壞了」。
 *
 * <p>硬要給它一個 4xx 反而會把 bug 偽裝成正常的業務結果。
 */
public class IllegalFetchJobTransitionException extends RuntimeException {

    public IllegalFetchJobTransitionException(FetchStatus from, FetchStatus to) {
        super("抓取任務不能從 %s 轉換到 %s".formatted(from, to));
    }
}
