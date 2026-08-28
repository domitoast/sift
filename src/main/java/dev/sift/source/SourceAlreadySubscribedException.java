package dev.sift.source;

/**
 * 新增訂閱來源時，這個使用者已經訂閱過同一個網址。
 *
 * <p>命名比照 {@code EmailAlreadyUsedException}：動詞用過去分詞
 * （{@code Subscribed} 而非 {@code Subscribe}），語意是「已經被訂閱」。
 *
 * <p>繼承 {@code RuntimeException}（unchecked），
 * 讓它一路往上拋到 {@code GlobalExceptionHandler}，
 * 中間每一層都不需要寫 try-catch 或 throws。
 *
 * <p>⚠️ 訊息中刻意不包含網址。例外訊息可能被寫進日誌，
 * 而使用者訂閱了什麼來源屬於個人資料。
 */
public class SourceAlreadySubscribedException extends RuntimeException {

    public SourceAlreadySubscribedException() {
        super("這個來源你已經訂閱過了");
    }
}
