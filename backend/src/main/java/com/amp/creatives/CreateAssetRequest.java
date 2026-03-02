package com.amp.creatives;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for registering a new creative asset.
 */
public record CreateAssetRequest(
        @NotNull(message = "clientId is required")
        UUID clientId,
        @NotBlank(message = "assetType is required")
        String assetType,
        @NotBlank(message = "originalFilename is required")
        String originalFilename,
        String mimeType,
        Long sizeBytes
) {
}
