package dev.sift.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 換發 access token 的請求。
 *
 * <p>只有 {@code @NotBlank}，刻意不驗證長度或字元格式——
 * 與 {@code LoginRequest} 同樣的考量：
 * 不讓攻擊者從 400（格式錯）與 401（驗證失敗）的差異中，
 * 推敲出 token 的長度與編碼方式。
 *
 * @param refreshToken 登入或上次換發時取得的原始值
 */
public record RefreshRequest(
        @NotBlank
        String refreshToken
) {
}
