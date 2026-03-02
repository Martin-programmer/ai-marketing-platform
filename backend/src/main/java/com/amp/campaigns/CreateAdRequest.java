package com.amp.campaigns;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request DTO for creating an ad within an adset.
 */
public record CreateAdRequest(
        UUID adsetId,
        @NotBlank(message = "name is required")
        String name,
        UUID creativePackageItemId
) {
}
