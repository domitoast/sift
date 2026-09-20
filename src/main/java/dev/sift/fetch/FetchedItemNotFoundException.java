package dev.sift.fetch;

/**
 * Article is missing or belongs to someone else; both return 404.
 */
public class FetchedItemNotFoundException extends RuntimeException {
    public FetchedItemNotFoundException() {
        super("找不到指定的文章");
    }
}
