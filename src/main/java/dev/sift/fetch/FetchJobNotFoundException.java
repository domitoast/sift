package dev.sift.fetch;

/**
 * Job is missing or belongs to someone else; both return 404.
 */
public class FetchJobNotFoundException extends RuntimeException {
    public FetchJobNotFoundException() {
        super("找不到指定的抓取任務");
    }
}
