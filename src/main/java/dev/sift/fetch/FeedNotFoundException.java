package dev.sift.fetch;

/**
 * 這個網址既不是 feed，網頁裡也找不到 feed 的宣告。
 *
 * <p>對應 400 Bad Request——這是使用者填錯了，不是伺服器的問題。
 *
 * <p>訊息刻意包含網址，因為使用者需要知道是「哪一個」填錯了。
 * 這與 {@code EmailAlreadyUsedException} 刻意不放 email 的判斷不同：
 * 那裡是個人資料，這裡是使用者剛剛自己輸入的東西。
 */
public class FeedNotFoundException extends RuntimeException {

    public FeedNotFoundException(String url) {
        super("這個網址沒有提供 RSS 或 Atom feed：" + url);
    }
}
