package dev.sift.source.dto;

import dev.sift.source.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for subscribing to a feed.
 */
public record CreateSourceRequest(

        @NotBlank
        @Size(max = 200)
        String name,

        @NotBlank
        @Size(max = 1000)
        @Pattern(regexp = "^https?://.+", message = "網址必須以 http:// 或 https:// 開頭")
        String url,

        @NotNull
        SourceType type
) {
}
