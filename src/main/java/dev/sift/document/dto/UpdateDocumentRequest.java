package dev.sift.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Payload for editing a document. The version field is required and is what
 * makes conflict detection possible.
 */
public record UpdateDocumentRequest(

        @NotBlank
        @Size(max = 500)
        String title,

        @NotBlank
        @Size(max = 1_000_000)
        String content,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
