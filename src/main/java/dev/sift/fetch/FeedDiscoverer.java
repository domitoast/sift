package dev.sift.fetch;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 從一份 HTML 裡找出 feed 的網址（RSS autodiscovery）。
 *
 * <p>網站的慣例是在 {@code <head>} 放一行標記，宣告「我的 feed 在這裡」：
 *
 * <pre>{@code
 * <link rel="alternate" type="application/rss+xml" href="/feed.xml">
 * }</pre>
 *
 * <p>這個慣例存在二十年了，訂閱器就是靠它讓使用者「貼首頁網址就能訂閱」。
 *
 * <p><b>這個類別不碰網路。</b> 給它一段 HTML 和一個 base 網址，
 * 它回傳找到的 feed 網址。因此測試是純 unit test。
 */
@Component
public class FeedDiscoverer {

    /**
     * 只認這兩種 type。
     *
     * <p>不寫成「只要 rel=alternate 就算」是刻意的——
     * {@code rel="alternate"} 也用在別的地方，最常見的是多語系版本：
     *
     * <pre>{@code
     * <link rel="alternate" hreflang="ja" href="/ja/">   ← 這是日文版，不是 feed
     * }</pre>
     *
     * 少了 type 的條件，我們會把日文版首頁當成 feed 去抓。
     */
    private static final String SELECTOR =
            "link[rel=alternate][type=application/rss+xml], "
            + "link[rel=alternate][type=application/atom+xml]";

    /**
     * @param html    網頁的原始內容
     * @param baseUrl 這份 HTML 是從哪個網址拿到的。
     *                用來把相對路徑（{@code /feed.xml}）補成完整網址
     * @return 找到的 feed 網址；找不到就是空的
     */
    public Optional<String> discover(String html, String baseUrl) {

        // 第二個參數就是 base URI，jsoup 靠它把相對路徑補成完整網址
        Document document = Jsoup.parse(html, baseUrl);

        Elements links = document.select(SELECTOR);

        for (Element link : links) {
            /*
             * "abs:href" 是 jsoup 的寫法，意思是「解析成絕對網址的 href」。
             *
             *   href="/feed.xml"  +  base "https://blog.com/posts"
             *        → "https://blog.com/feed.xml"
             *
             * 自己處理相對路徑很容易錯：
             * "feed.xml"（沒有斜線）、"../feed.xml"、"//other.com/feed.xml"
             * 這三種的規則都不一樣。
             */
            String href = link.attr("abs:href");

            if (!href.isBlank()) {
                return Optional.of(href);
            }
        }

        return Optional.empty();
    }
}
