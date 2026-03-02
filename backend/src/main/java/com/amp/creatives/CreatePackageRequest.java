package com.amp.creatives;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a creative package.
 */
public record CreatePackageRequest(
        @NotNull(message = "clientId is required")
        UUID clientId,
        @NotBlank(message = "name is required")
        String name,
        String objective
) {
}
