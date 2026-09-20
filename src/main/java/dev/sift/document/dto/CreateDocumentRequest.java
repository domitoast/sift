package dev.sift.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a document.
 */
public record CreateDocumentRequest(

        @NotBlank
        @Size(max = 500)
        String title,

        @NotBlank
        @Size(max = 1_000_000)
        String content
) {
}
