package dev.sift.summarize;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Composite key of user and date.
 */
public record LlmUsageId(Long userId, LocalDate usageDate) implements Serializable {
}
