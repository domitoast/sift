package dev.sift.fetch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        FetchedArticle second = parser.parse(RSS).get(1);

        assertThat(second.title()).isEqualTo("第二篇文章");
        assertThat(second.publishedAt()).isNull();
    }

    @Test
    @DisplayName("★ Atom 的 <entry> 也能解析——這就是用 Rome 的理由")
    void parse_atom_shouldAlsoWork() {
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
    @DisplayName("★ RSS 的內文在 <description>")
    void parse_rssDescription_shouldBecomeContent() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>測試</title>
                    <item>
                      <title>有內文的文章</title>
                      <link>https://example.com/1</link>
                      <description>這是文章的內容。</description>
                    </item>
                  </channel>
                </rss>
                """;

        assertThat(parser.parse(rss).get(0).content()).isEqualTo("這是文章的內容。");
    }

    @Test
    @DisplayName("★ Atom 的內文在 <content>")
    void parse_atomContent_shouldBecomeContent() {
        String atom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>測試</title>
                  <entry>
                    <title>Atom 文章</title>
                    <link href="https://example.com/a"/>
                    <content>Atom 的內文放在這裡。</content>
                  </entry>
                </feed>
                """;

        assertThat(parser.parse(atom).get(0).content()).isEqualTo("Atom 的內文放在這裡。");
    }

    @Test
    @DisplayName("★★ HTML 標籤要清乾淨——LLM 不該花錢讀那些標籤")
    void parse_htmlContent_shouldBeStripped() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>測試</title>
                    <item>
                      <title>有 HTML 的文章</title>
                      <link>https://example.com/1</link>
                      <description>&lt;p&gt;今天發表了新版本，&lt;a href="https://x.com"&gt;詳見公告&lt;/a&gt;。&lt;/p&gt;</description>
                    </item>
                  </channel>
                </rss>
                """;

        String content = parser.parse(rss).get(0).content();

        assertThat(content).isEqualTo("今天發表了新版本，詳見公告。");
        assertThat(content).doesNotContain("<");
        assertThat(content).doesNotContain("href");
    }

    @Test
    @DisplayName("★★ 多段 HTML 要保留分段——不然三千字會變成一整坨")
    void parse_multipleParagraphs_shouldKeepBreaks() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>測試</title>
                    <item>
                      <title>有分段的文章</title>
                      <link>https://example.com/1</link>
                      <description>&lt;p&gt;第一段。&lt;/p&gt;&lt;p&gt;第二段。&lt;br&gt;同一段的第二行。&lt;/p&gt;</description>
                    </item>
                  </channel>
                </rss>
                """;

        String content = parser.parse(rss).get(0).content();

        assertThat(content).contains("第一段。\n\n第二段。");

        assertThat(content).contains("第二段。\n同一段的第二行。");

        assertThat(content).doesNotContain("<");

        assertThat(content).doesNotContain("\n\n\n");
    }

    @Test
    @DisplayName("★ 沒有內文是正常的，content 為 null 而不是爆掉")
    void parse_withoutContent_shouldGiveNull() {
        assertThat(parser.parse(RSS).get(1).content()).isNull();
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
