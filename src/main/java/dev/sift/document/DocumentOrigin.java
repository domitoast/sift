package dev.sift.document;

/**
 * 文件的來源。
 *
 * <p>目前只會產生 {@link #MANUAL}——自動管線要到 Day 11 才有。
 */
public enum DocumentOrigin {

    /** 使用者自己建立的。 */
    MANUAL,

    /** 由管線抓取的文章 promote 而來，必定有對應的 fetched_item。 */
    FETCHED
}
