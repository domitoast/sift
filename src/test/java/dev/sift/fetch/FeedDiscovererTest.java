package dev.sift.fetch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FeedDiscovererTest {
    private final FeedDiscoverer discoverer = new FeedDiscoverer();

    private static final String BASE = "https://blog.example.com/posts";

    @Test
    @DisplayName("找到 RSS 的宣告")
    void discover_rssLink_shouldFind() {
        String html = """
                <html><head>
                  <title>某部落格</title>
                  <link rel="alternate" type="application/rss+xml" href="https://blog.example.com/feed.xml">
                </head><body>內容</body></html>
                """;

        assertThat(discoverer.discover(html, BASE))
                .contains("https://blog.example.com/feed.xml");
    }

    @Test
    @DisplayName("★ 相對路徑要補成完整網址")
    void discover_relativeHref_shouldResolveToAbsolute() {
        String html = """
                <html><head>
                  <link rel="alternate" type="application/rss+xml" href="/feed.xml">
                </head></html>
                """;

        assertThat(discoverer.discover(html, BASE))
                .contains("https://blog.example.com/feed.xml");
    }

    @Test
    @DisplayName("★ 大小寫、單引號、屬性順序都不影響")
    void discover_messyHtml_shouldStillFind() {
        String html = """
                <HTML><HEAD>
                  <LINK HREF='/atom.xml' TYPE="application/atom+xml" REL='alternate'/>
                </HEAD></HTML>
                """;

        assertThat(discoverer.discover(html, BASE))
                .contains("https://blog.example.com/atom.xml");
    }

    @Test
    @DisplayName("★★ 多語系的 rel=alternate 不可以被當成 feed")
    void discover_hreflangAlternate_shouldBeIgnored() {
        String html = """
                <html><head>
                  <link rel="alternate" hreflang="ja" href="/ja/">
                  <link rel="alternate" hreflang="en" href="/en/">
                </head></html>
                """;

        assertThat(discoverer.discover(html, BASE)).isEmpty();
    }

    @Test
    @DisplayName("完全沒有 feed 宣告 → 空的")
    void discover_noFeed_shouldBeEmpty() {
        String html = "<html><head><title>沒有 feed 的網站</title></head></html>";

        assertThat(discoverer.discover(html, BASE)).isEmpty();
    }

    @Test
    @DisplayName("同時有 RSS 與 Atom → 取第一個就好")
    void discover_bothFormats_shouldReturnFirst() {
        String html = """
                <html><head>
                  <link rel="alternate" type="application/rss+xml" href="/rss.xml">
                  <link rel="alternate" type="application/atom+xml" href="/atom.xml">
                </head></html>
                """;

        Optional<String> found = discoverer.discover(html, BASE);

        assertThat(found).isPresent();
        assertThat(found.get()).startsWith("https://blog.example.com/");
    }
}
