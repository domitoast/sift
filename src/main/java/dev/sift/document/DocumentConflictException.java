package dev.sift.document;

/**
 * 編輯衝突：這篇文件在你讀取之後、送出之前，被別人改過了。
 *
 * <p>回應 409 Conflict——語意是「你的請求格式沒錯，
 * 但與伺服器目前的狀態衝突」。
 *
 * <p>不是 400（格式問題）也不是 403（權限問題）。
 * 這與 Day 5 的「email 已被註冊」用同一個狀態碼，理由相同。
 */
public class DocumentConflictException extends RuntimeException {

    private final Long currentVersion;

    public DocumentConflictException(Long currentVersion) {
        super("這篇文件已被修改，請重新載入後再編輯");
        this.currentVersion = currentVersion;
    }

    /**
     * 目前資料庫裡的版本號。
     *
     * <p>回傳給呼叫端是刻意的——前端可以據此判斷「我落後了幾個版本」，
     * 或直接重新載入。這是自己的文件，不涉及資訊洩漏。
     */
    public Long getCurrentVersion() {
        return currentVersion;
    }
}
