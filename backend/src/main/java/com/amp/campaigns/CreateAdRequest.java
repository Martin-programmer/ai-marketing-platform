package com.amp.campaigns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating an ad within an adset.
 */
public record CreateAdRequest(
        @NotNull(message = "adsetId is required")
        UUID adsetId,
        @NotBlank(message = "name is required")
        String name,
        UUID creativePackageItemId
) {
}
