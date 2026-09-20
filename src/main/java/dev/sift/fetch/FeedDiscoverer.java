package dev.sift.fetch;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Finds the feed URL inside an HTML page's {@code <link rel="alternate">} tags,
 * so users can paste a site's home page instead of hunting for the feed.
 */
@Component
public class FeedDiscoverer {
    private static final String SELECTOR =
            "link[rel=alternate][type=application/rss+xml], "
            + "link[rel=alternate][type=application/atom+xml]";

    public Optional<String> discover(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);

        Elements links = document.select(SELECTOR);

        for (Element link : links) {
            String href = link.attr("abs:href");

            if (!href.isBlank()) {
                return Optional.of(href);
            }
        }

        return Optional.empty();
    }
}
