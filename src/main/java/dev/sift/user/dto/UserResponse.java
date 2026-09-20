package dev.sift.user.dto;

import dev.sift.user.User;

import dev.sift.summarize.LlmProvider;

import java.time.Instant;

/**
 * Public account fields. Never includes the password hash or the API key.
 */
public record UserResponse(
        Long id,
        String email,
        Instant createdAt,
        String llmApiKeyMasked,
        LlmProvider llmProvider
) {
    public static UserResponse from(User user) {
        return from(user, null);
    }

    public static UserResponse from(User user, String llmApiKeyMasked) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getCreatedAt(),
                llmApiKeyMasked,
                user.getLlmProvider()
        );
    }
}
