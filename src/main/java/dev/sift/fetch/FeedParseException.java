package dev.sift.fetch;

/**
 * Something was downloaded, but it is not a valid feed.
 */
public class FeedParseException extends RuntimeException {
    public FeedParseException(String reason) {
        super("無法解析 feed：" + reason);
    }
}
