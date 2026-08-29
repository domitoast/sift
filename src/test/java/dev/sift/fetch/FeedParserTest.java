package dev.sift.fetch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FeedParser 的 unit test。
 *
 * <p><b>沒有 {@code @SpringBootTest}，也不連任何網站。</b>
 * 每一題的輸入都是寫死在這個檔案裡的字串，
 * 所以結果永遠可預測——Hacker News 掛掉不會讓這些測試變紅。
 */
class FeedParserTest {

    private final FeedParser parser = new FeedParser();

    private static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>測試用 Feed</title>
                <link>https://example.com/</link>
                <item>
                  <title>第一篇文章</title>
                  <link>https://example.com/1</link>
                  <pubDate>Thu, 28 Aug 2026 08:15:00 GMT</pubDate>
                </item>
                <item>
                  <title>第二篇文章</title>
                  <link>https://example.com/2</link>
                </item>
              </channel>
            </rss>
            """;

    @Test
    @DisplayName("RSS：兩個 item 解析成兩篇文章，順序不變")
    void parse_rss_shouldReturnArticles() {

        List<FetchedArticle> articles = parser.parse(RSS);

        assertThat(articles).hasSize(2);
        assertThat(articles.get(0).title()).isEqualTo("第一篇文章");
        assertThat(articles.get(0).link()).isEqualTo("https://example.com/1");
    }

    @Test
    @DisplayName("有 pubDate 就轉成 Instant")
    void parse_withPubDate_shouldParseTime() {

        FetchedArticle first = parser.parse(RSS).get(0);

        assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-08-28T08:15:00Z"));
    }

    @Test
    @DisplayName("★ 沒有 pubDate 是正常情況，不是錯誤——publishedAt 為 null")
    void parse_withoutPubDate_shouldGiveNull() {

        /*
         * 很多 feed 根本不填發布時間。
         * 如果這裡設計成「沒有時間就丟例外」，一堆正常的來源會被判定為失敗。
         */
        FetchedArticle second = parser.parse(RSS).get(1);

        assertThat(second.title()).isEqualTo("第二篇文章");
        assertThat(second.publishedAt()).isNull();
    }

    @Test
    @DisplayName("★ Atom 的 <entry> 也能解析——這就是用 Rome 的理由")
    void parse_atom_shouldAlsoWork() {

        /*
         * 注意這份的標籤跟上面的 RSS 完全不同：
         *   RSS  <item>  <link>https://...</link>
         *   Atom <entry> <link href="https://..."/>
         *
         * 我們的程式碼一行都沒有為 Atom 寫過，Rome 把差異吸收掉了。
         */
        String atom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>某個 GitHub Repo 的 Releases</title>
                  <entry>
                    <title>v2.0.0</title>
                    <link href="https://github.com/x/y/releases/v2.0.0"/>
                  </entry>
                </feed>
                """;

        List<FetchedArticle> articles = parser.parse(atom);

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).title()).isEqualTo("v2.0.0");
        assertThat(articles.get(0).link()).isEqualTo("https://github.com/x/y/releases/v2.0.0");
    }

    @Test
    @DisplayName("★ 沒有 link 的項目會被略過")
    void parse_itemWithoutLink_shouldBeSkipped() {

        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>測試</title>
                    <item>
                      <title>沒有連結的文章</title>
                    </item>
                    <item>
                      <title>正常的文章</title>
                      <link>https://example.com/ok</link>
                    </item>
                  </channel>
                </rss>
                """;

        List<FetchedArticle> articles = parser.parse(rss);

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).title()).isEqualTo("正常的文章");
    }

    @Test
    @DisplayName("★ 拿到 HTML 首頁而不是 feed → FeedParseException")
    void parse_html_shouldThrow() {

        /*
         * 這是最常見的失敗：使用者填了網站首頁的網址，不是 feed 的網址。
         *
         * Day 16 的 RSS autodiscovery 要處理的就是這個情況——
         * 拿到 HTML 之後，去它的 <head> 裡找真正的 feed 網址。
         */
        String html = "<html><head><title>某個網站</title></head><body>歡迎</body></html>";

        assertThatThrownBy(() -> parser.parse(html))
                .isInstanceOf(FeedParseException.class);
    }

    @Test
    @DisplayName("★ 完全不是 XML → FeedParseException，而不是其他奇怪的例外")
    void parse_garbage_shouldThrow() {

        assertThatThrownBy(() -> parser.parse("這根本不是 XML"))
                .isInstanceOf(FeedParseException.class);
    }

    @Test
    @DisplayName("★★ 帶 DOCTYPE 的 feed 一律拒絕（XXE）")
    void parse_withDoctype_shouldThrow() {

        /*
         * 攻擊情境：
         *   1. 攻擊者自己架一個網站，網址是 https://evil.example.com/rss
         *   2. 他把這個網址加成訂閱來源——它是 https 開頭，通過 Day 13 的檢查
         *   3. 排程去抓，拿回下面這段「feed」
         *   4. 如果解析器允許 DOCTYPE，&xxe; 會被替換成
         *      伺服器上 /etc/passwd 的內容
         *   5. 那段內容變成文章標題，存進資料庫，攻擊者回來讀自己的知識庫
         *
         * 這叫 XXE（XML External Entity）。
         *
         * 這個測試存在的意義：如果哪天有人把 setAllowDoctypes 改成 true，
         * 或換掉解析函式庫，這一題會紅。
         */
        String malicious = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE rss [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <rss version="2.0">
                  <channel>
                    <title>&xxe;</title>
                    <item>
                      <title>看起來很正常的文章</title>
                      <link>https://evil.example.com/1</link>
                    </item>
                  </channel>
                </rss>
                """;

        assertThatThrownBy(() -> parser.parse(malicious))
                .isInstanceOf(FeedParseException.class);
    }
}
