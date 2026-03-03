package com.amp.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a user account.
 */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String role,
        String status,
        UUID agencyId,
        UUID clientId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static UserResponse from(UserAccount u) {
        return new UserResponse(
                u.getId(), u.getEmail(), u.getDisplayName(),
                u.getRole(), u.getStatus(),
                u.getAgencyId(), u.getClientId(),
                u.getCreatedAt(), u.getUpdatedAt()
        );
    }
}
