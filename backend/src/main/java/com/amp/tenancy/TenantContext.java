package com.amp.tenancy;

import java.util.UUID;

/**
 * Immutable holder for the current tenant context (agency, user, role).
 * Populated per-request by the authentication filter and stored
 * in {@link TenantContextHolder}.
 */
public class TenantContext {

    private final UUID agencyId;
    private final UUID userId;
    private final String email;
    private final String role;

    public TenantContext(UUID agencyId, UUID userId, String email, String role) {
        this.agencyId = agencyId;
        this.userId = userId;
        this.email = email;
        this.role = role;
    }

    public UUID getAgencyId() { return agencyId; }
    public UUID getUserId()   { return userId; }
    public String getEmail()  { return email; }
    public String getRole()   { return role; }

    public boolean isOwnerAdmin() { return "OWNER_ADMIN".equals(role); }
}
