package dev.sift.fetch;

/**
 * 拿到的東西不是一份能解析的 feed。
 *
 * <p>常見原因：網址其實是一個 HTML 首頁、對方回了錯誤頁面、XML 格式壞掉。
 *
 * <p><b>這一律歸類為 {@link FailureType#PERMANENT}</b>——
 * 明天再抓一次還是同一份壞掉的內容，重試沒有意義。
 * 需要人去看那個網址到底對不對。
 */
public class FeedParseException extends RuntimeException {

    public FeedParseException(String reason) {
        super("無法解析 feed：" + reason);
    }
}
