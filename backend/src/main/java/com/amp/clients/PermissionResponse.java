package com.amp.clients;

import java.util.UUID;

/**
 * Response DTO for a user's permission on a client.
 */
public record PermissionResponse(
        UUID userId,
        String userEmail,
        String userDisplayName,
        String permission
) {}
