package dev.sift.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh payload.
 */
public record RefreshRequest(
        @NotBlank
        String refreshToken
) {
}
