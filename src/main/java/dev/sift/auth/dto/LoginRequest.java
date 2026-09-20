package dev.sift.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

/**
 * Login payload.
 */
public record LoginRequest(

        @NotBlank(message = "email 不可為空")
        String email,

        @NotBlank(message = "密碼不可為空")
        String password
) {
    public LoginRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
