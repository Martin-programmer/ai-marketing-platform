package com.amp.agency;

import java.util.UUID;

/**
 * Response DTO returned when creating an agency with its admin user.
 */
public record CreateAgencyWithAdminResponse(
        AgencyResponse agency,
        AdminUserInfo adminUser
) {
    public record AdminUserInfo(
            UUID id,
            String email,
            String role
    ) {}
}
