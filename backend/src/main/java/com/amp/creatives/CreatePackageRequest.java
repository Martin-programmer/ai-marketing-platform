package com.amp.creatives;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request DTO for creating a creative package.
 */
public record CreatePackageRequest(
        UUID clientId,
        @NotBlank(message = "name is required")
        String name,
        String objective
) {
}
