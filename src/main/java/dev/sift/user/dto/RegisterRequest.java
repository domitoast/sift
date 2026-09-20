package dev.sift.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * Registration payload.
 */
public record RegisterRequest(

        @NotBlank(message = "email 不可為空")
        @Email(message = "email 格式不正確")
        @Size(max = 255, message = "email 長度不可超過 255 字元")
        String email,

        @NotBlank(message = "密碼不可為空")
        @Size(min = 8, max = 72, message = "密碼長度須介於 8 到 72 字元")
        String password
) {
    public RegisterRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
