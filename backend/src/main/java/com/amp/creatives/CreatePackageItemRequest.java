package com.amp.creatives;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePackageItemRequest(
        @NotNull(message = "creativeAssetId is required")
        UUID creativeAssetId,
        @NotNull(message = "copyVariantId is required")
        UUID copyVariantId,
        @NotBlank(message = "ctaType is required")
        String ctaType,
        @NotBlank(message = "destinationUrl is required")
        String destinationUrl,
        @Min(value = 1, message = "weight must be at least 1")
        @Max(value = 100, message = "weight must be at most 100")
        Integer weight
) {
}