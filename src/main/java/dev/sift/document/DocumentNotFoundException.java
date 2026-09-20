package dev.sift.document;

/**
 * Document is missing or belongs to someone else. Both cases return 404
 * so ids cannot be enumerated.
 */
public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException() {
        super("找不到指定的文件");
    }
}
