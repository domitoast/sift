package dev.sift.fetch;

/**
 * The URL is neither a feed nor a page that advertises one.
 */
public class FeedNotFoundException extends RuntimeException {
    public FeedNotFoundException(String url) {
        super("這個網址沒有提供 RSS 或 Atom feed：" + url);
    }
}
