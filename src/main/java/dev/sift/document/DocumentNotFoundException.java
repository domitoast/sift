package dev.sift.document;

/**
 * 找不到文件——「不存在」與「不是你的」共用這一個例外。
 *
 * <p>兩種情況故意不區分：若「別人的文件」回 403、「不存在」回 404，
 * 呼叫端就能靠狀態碼的差異列舉出哪些 id 真的存在（ADR-006）。
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException() {
        super("找不到指定的文件");
    }
}
