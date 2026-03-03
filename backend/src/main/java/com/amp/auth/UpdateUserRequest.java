package com.amp.auth;

import java.util.UUID;

/**
 * Request DTO for updating an existing user account.
 */
public record UpdateUserRequest(
        String displayName,
        String role,
        String status
) {}
