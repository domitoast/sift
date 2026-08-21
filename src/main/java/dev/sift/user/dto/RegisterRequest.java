package dev.sift.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * 註冊請求的輸入資料。
 *
 * <p>對應 API：{@code POST /api/v1/auth/register}
 *
 * <p><b>安全設計</b>：{@code password} 是明文，僅存在於
 * 「HTTP 請求進來」到「Service 完成雜湊」之間的短暫期間，
 * 絕不會被儲存，也絕不會出現在任何回應中。
 *
 * <p>⚠️ record 會自動產生包含所有欄位的 {@code toString()}，
 * 其中含有明文密碼。**絕對不可把整個 RegisterRequest 寫進日誌。**
 *
 * @param email    登入帳號（建立物件時會自動正規化）
 * @param password 明文密碼
 */
public record RegisterRequest(

        @NotBlank(message = "email 不可為空")
        @Email(message = "email 格式不正確")
        @Size(max = 255, message = "email 長度不可超過 255 字元")
        String email,

        /*
         * 下限 8：目前普遍接受的最低標準。
         *
         * 上限 72：BCrypt 的技術限制——它只處理前 72 個位元組，
         * 超過的部分被靜默忽略。不設上限的話，使用者設了 100 字元的密碼，
         * 實際只有前 72 個有效，這是個隱蔽且危險的行為。
         */
        @NotBlank(message = "密碼不可為空")
        @Size(min = 8, max = 72, message = "密碼長度須介於 8 到 72 字元")
        String password
) {

    /**
     * Compact constructor（精簡建構子）——在物件建立時正規化 email。
     *
     * <p>語法說明：{@code public RegisterRequest {}} 沒有參數列，
     * 這是 record 專屬的寫法。方法內可以修改參數變數，
     * 修改後的值才會被指派到欄位。
     *
     * <p><b>為什麼正規化必須放在這裡，而不是 Service</b>：
     *
     * <p>執行順序是「Jackson 建立物件 → 驗證註解檢查 → 進入 Controller」。
     * 若正規化寫在 Service，驗證會先看到未清理的原始輸入——
     * 使用者輸入 {@code "  a@b.com  "} 會被 {@code @Email} 判定為格式錯誤，
     * 而他盯著畫面上看起來完全正確的 email 完全不知道問題在哪。
     *
     * <p>這是 Day 5 由整合測試
     * {@code register_shouldNormalizeEmailBeforePersisting} 發現的設計缺陷。
     *
     * <p><b>原則</b>：資料一進入系統邊界就清理乾淨，
     * 之後每一層都能假設它是乾淨的。
     *
     * <p>{@code Locale.ROOT} 的用意：土耳其語的大寫 I 轉小寫會變成 ı
     * （無點的 i）。若使用系統預設語系，同一段程式在不同機器上結果不同，
     * 這是著名的 "Turkish I problem"。
     *
     * <p>此處必須做 null 檢查——compact constructor 比 {@code @NotBlank}
     * 更早執行，若 email 為 null 會直接拋 NullPointerException，
     * 使用者就看不到「email 不可為空」這個友善訊息了。
     */
    public RegisterRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
