package dev.sift.source.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update: null fields mean "leave unchanged".
 */
public record UpdateSourceRequest(

        @Size(max = 200)
        String name,

        Boolean enabled
) {
}
