package dev.sift.fetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Turns whatever the user typed into a URL we know works: try it as a feed,
 * fall back to autodiscovery, then verify the discovered URL actually parses.
 */
@Service
public class FeedResolver {
    private static final Logger log = LoggerFactory.getLogger(FeedResolver.class);

    private final FetchClient fetchClient;
    private final FeedParser feedParser;
    private final FeedDiscoverer feedDiscoverer;

    public FeedResolver(FetchClient fetchClient,
                        FeedParser feedParser,
                        FeedDiscoverer feedDiscoverer) {
        this.fetchClient = fetchClient;
        this.feedParser = feedParser;
        this.feedDiscoverer = feedDiscoverer;
    }

    public String resolve(String url) {
        String content = fetchClient.fetch(url);

        try {
            List<FetchedArticle> articles = feedParser.parse(content);
            log.info("網址本身就是 feed url={} 文章數={}", url, articles.size());
            return url;
        } catch (FeedParseException e) {
            log.debug("不是 feed，改用 autodiscovery url={}", url);
        }

        Optional<String> discovered = feedDiscoverer.discover(content, url);

        if (discovered.isEmpty()) {
            throw new FeedNotFoundException(url);
        }

        String feedUrl = discovered.get();
        log.info("autodiscovery 找到 feed 網址 原始={} feed={}", url, feedUrl);

        try {
            String feedContent = fetchClient.fetch(feedUrl);
            feedParser.parse(feedContent);

            return feedUrl;
        } catch (FeedParseException | FeedFetchException e) {
            log.warn("autodiscovery 找到的網址無法使用 feed={} 原因={}", feedUrl, e.getMessage());
            throw new FeedNotFoundException(url);
        }
    }
}
