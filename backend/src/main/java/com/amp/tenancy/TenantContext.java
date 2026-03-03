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
    private final UUID clientId;

    public TenantContext(UUID agencyId, UUID userId, String email, String role) {
        this(agencyId, userId, email, role, null);
    }

    public TenantContext(UUID agencyId, UUID userId, String email, String role, UUID clientId) {
        this.agencyId = agencyId;
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.clientId = clientId;
    }

    public UUID getAgencyId() { return agencyId; }
    public UUID getUserId()   { return userId; }
    public String getEmail()  { return email; }
    public String getRole()   { return role; }
    public UUID getClientId() { return clientId; }

    public boolean isOwnerAdmin() { return "OWNER_ADMIN".equals(role); }
    public boolean isClientUser() { return "CLIENT_USER".equals(role); }
}
