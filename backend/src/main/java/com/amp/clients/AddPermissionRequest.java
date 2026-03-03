package com.amp.clients;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for adding a single user permission to a client.
 */
public record AddPermissionRequest(
        @NotNull(message = "userId is required")
        UUID userId,

        @NotBlank(message = "permission is required")
        String permission
) {}
