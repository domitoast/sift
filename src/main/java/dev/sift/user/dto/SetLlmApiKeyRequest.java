package dev.sift.user.dto;

import dev.sift.summarize.LlmProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Provider plus API key. toString masks the key so it cannot leak into logs.
 */
public record SetLlmApiKeyRequest(

        @NotNull(message = "請選擇 LLM 供應商")
        LlmProvider provider,

        @NotBlank(message = "API key 不可為空")
        @Size(max = 500, message = "API key 過長")
        String apiKey
) {
    @Override
    public String toString() {
        return "SetLlmApiKeyRequest[provider=%s, apiKey=***]".formatted(provider);
    }
}
