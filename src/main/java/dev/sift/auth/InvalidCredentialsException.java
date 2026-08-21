package dev.sift.auth;

/**
 * 登入失敗：帳號不存在或密碼錯誤。
 *
 * <p>⚠️ <b>刻意「不」區分是哪一種。</b>
 *
 * <p>若回應「此帳號不存在」，攻擊者就能用它來確認哪些 email
 * 註冊過本站——這叫 <b>user enumeration（帳號列舉）</b>。
 * 拿到一份有效帳號清單之後，他就能針對這些帳號做密碼嘗試，
 * 或是拿去其他網站試（很多人到處用同一組密碼）。
 *
 * <p>因此兩種情況都回同一句話、同一個狀態碼。
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("帳號或密碼錯誤");
    }
}
