package dev.sift.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

/**
 * 登入請求。
 *
 * <p>與 RegisterRequest 的差別：這裡<b>不做格式驗證</b>
 * （沒有 @Email、沒有 @Size）。
 *
 * <p>理由：登入時我們不在乎輸入是否符合格式，只在乎「對不對」。
 * 若對格式不合的輸入回傳「email 格式不正確」，等於告訴攻擊者
 * 「這個輸入連格式都不對，換一個吧」——這是不必要的資訊。
 * 一律回「帳號或密碼錯誤」即可。
 *
 * <p>{@code @NotBlank} 仍然保留，因為空白輸入根本不需要查資料庫。
 *
 * @param email    登入帳號（建立時自動正規化）
 * @param password 明文密碼
 */
public record LoginRequest(

        @NotBlank(message = "email 不可為空")
        String email,

        @NotBlank(message = "密碼不可為空")
        String password
) {

    /**
     * 與 RegisterRequest 相同的正規化處理——
     * 註冊時存的是小寫去空白的版本，登入時查詢也必須用相同形式，
     * 否則使用者輸入 "Kevin@Example.com" 會查不到自己的帳號。
     */
    public LoginRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
