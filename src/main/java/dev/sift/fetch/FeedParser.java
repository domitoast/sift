package dev.sift.fetch;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Turns raw feed XML into articles. DOCTYPE processing is disabled to block XXE.
 */
@Component
public class FeedParser {
    private static final Logger log = LoggerFactory.getLogger(FeedParser.class);

    public List<FetchedArticle> parse(String rawFeed) {
        SyndFeedInput input = new SyndFeedInput();

        input.setAllowDoctypes(false);

        SyndFeed feed;
        try {
            feed = input.build(new StringReader(rawFeed));
        } catch (FeedException | IllegalArgumentException e) {
            throw new FeedParseException(e.getMessage());
        }

        List<FetchedArticle> articles = feed.getEntries().stream()
                .map(this::toArticle)
                .filter(article -> article != null)
                .toList();

        log.debug("解析完成 feed 標題={} 取得文章數={}", feed.getTitle(), articles.size());

        return articles;
    }

    private FetchedArticle toArticle(SyndEntry entry) {
        String title = trimToNull(entry.getTitle());
        String link = trimToNull(entry.getLink());

        if (title == null || link == null) {
            log.debug("略過不完整的項目 title={} link={}", title, link);
            return null;
        }

        return new FetchedArticle(title, link, extractContent(entry),
                toInstant(entry.getPublishedDate()));
    }

    private String extractContent(SyndEntry entry) {
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            String cleaned = stripHtml(entry.getContents().get(0).getValue());
            if (cleaned != null) {
                return cleaned;
            }
        }

        if (entry.getDescription() != null) {
            return stripHtml(entry.getDescription().getValue());
        }

        return null;
    }

    private String stripHtml(String html) {
        String trimmed = trimToNull(html);
        if (trimmed == null) {
            return null;
        }

        org.jsoup.nodes.Document doc = Jsoup.parse(trimmed);
        doc.outputSettings(new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));

        doc.select("br").after("\\n");

        doc.select("p, div, li, h1, h2, h3, h4, h5, h6, blockquote, pre, tr")
                .after("\\n\\n");

        String text = doc.text()
                .replace("\\n", "\n")

                .replaceAll("[ \t]*\n[ \t]*", "\n")

                .replaceAll("\n{3,}", "\n\n")
                .trim();

        return trimToNull(text);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
