package com.amp.reports;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating feedback on a report or suggestion.
 */
public record CreateFeedbackRequest(
        @NotNull(message = "clientId is required")
        UUID clientId,
        @NotBlank(message = "entityType is required")
        String entityType,
        @NotNull(message = "entityId is required")
        UUID entityId,
        @NotNull(message = "rating is required")
        @Min(1) @Max(5)
        Integer rating,
        String comment
) {
}
