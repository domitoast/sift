package dev.sift.user;

/**
 * 註冊時 email 已被使用。
 *
 * <p>繼承 {@code RuntimeException} 而非 {@code Exception}，
 * 也就是所謂的 unchecked exception——呼叫端不需要寫 try-catch 或 throws。
 *
 * <p><b>為什麼選 unchecked</b>：
 * 這個例外會一路往上拋到全域例外處理器（Day5-E），
 * 中間每一層都不需要、也不應該處理它。
 * 若用 checked exception，Controller 到 Service 之間每一層
 * 都要寫 {@code throws}，形成無意義的樣板程式碼。
 *
 * <p>Spring 生態系的慣例是：業務例外一律用 unchecked。
 *
 * <p>⚠️ 訊息中刻意不包含 email 值。
 * 例外訊息很可能被寫進日誌，而 email 屬於個人資料。
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("此 email 已被註冊");
    }
}
