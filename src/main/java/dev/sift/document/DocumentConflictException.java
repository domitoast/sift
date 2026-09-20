package dev.sift.document;

/**
 * The submitted version is stale; someone else edited the document first.
 */
public class DocumentConflictException extends RuntimeException {
    private final Long currentVersion;

    public DocumentConflictException(Long currentVersion) {
        super("這篇文件已被修改，請重新載入後再編輯");
        this.currentVersion = currentVersion;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }
}
