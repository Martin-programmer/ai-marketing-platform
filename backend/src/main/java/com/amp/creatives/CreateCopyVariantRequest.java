package com.amp.creatives;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a copy variant.
 */
public record CreateCopyVariantRequest(
        @NotNull(message = "clientId is required")
        UUID clientId,
        UUID assetId,
        @NotBlank(message = "primaryText is required")
        String primaryText,
        @NotBlank(message = "headline is required")
        String headline,
        String description,
        String cta,
        String tone,
        String language
) {
    public CreateCopyVariantRequest {
        if (language == null || language.isBlank()) language = "bg";
        if (cta == null || cta.isBlank()) cta = "LEARN_MORE";
    }
}
