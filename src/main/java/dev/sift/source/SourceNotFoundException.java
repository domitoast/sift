package dev.sift.source;

/**
 * Source is missing or belongs to someone else; both return 404.
 */
public class SourceNotFoundException extends RuntimeException {
    public SourceNotFoundException() {
        super("找不到指定的訂閱來源");
    }
}
