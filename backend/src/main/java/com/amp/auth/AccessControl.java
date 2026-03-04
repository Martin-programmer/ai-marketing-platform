package com.amp.auth;

import com.amp.clients.UserClientPermissionRepository;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Central authorization service. Every controller method should use this
 * to check access instead of doing ad-hoc role checks.
 * <p>
 * Access hierarchy:
 * <ul>
 *   <li>OWNER_ADMIN — platform superadmin, agency_id is NULL, sees everything</li>
 *   <li>AGENCY_ADMIN — full access to all clients within own agency</li>
 *   <li>AGENCY_USER — access ONLY to assigned clients with specific permissions</li>
 *   <li>CLIENT_USER — read-only portal access for own client only</li>
 * </ul>
 */
@Component
public class AccessControl {

    private static final Set<String> AGENCY_ROLES =
            Set.of("OWNER_ADMIN", "AGENCY_ADMIN", "AGENCY_USER");

    private final UserClientPermissionRepository permissionRepository;

    public AccessControl(UserClientPermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    private TenantContext ctx() {
        return TenantContextHolder.require();
    }

    // ── Role checks ───────────────────────────────────────────

    public boolean isOwner() {
        return "OWNER_ADMIN".equals(ctx().getRole());
    }

    public boolean isAgencyAdmin() {
        return "AGENCY_ADMIN".equals(ctx().getRole());
    }

    public boolean isAgencyUser() {
        return "AGENCY_USER".equals(ctx().getRole());
    }

    public boolean isClientUser() {
        return "CLIENT_USER".equals(ctx().getRole());
    }

    public boolean isAgencyLevel() {
        return AGENCY_ROLES.contains(ctx().getRole());
    }

    // ── Agency access ─────────────────────────────────────────

    /**
     * Require that current user belongs to the given agency (or is OWNER_ADMIN).
     */
    public void requireAgencyAccess(UUID agencyId) {
        if (isOwner()) return; // Owner sees everything
        UUID userAgency = ctx().getAgencyId();
        if (userAgency == null || !userAgency.equals(agencyId)) {
            throw new AccessDeniedException("Access denied to this agency");
        }
    }

    /**
     * Get the agency ID for the current user.
     * For OWNER_ADMIN, returns null (must be passed explicitly).
     */
    public UUID currentAgencyId() {
        return ctx().getAgencyId();
    }

    /**
     * Require agency-level role (OWNER_ADMIN, AGENCY_ADMIN, AGENCY_USER).
     * Blocks CLIENT_USER.
     */
    public void requireAgencyRole() {
        if (!isAgencyLevel()) {
            throw new AccessDeniedException("Agency role required");
        }
    }

    // ── Client access ─────────────────────────────────────────

    /**
     * Check if current user can access a specific client with a specific permission.
     * <ul>
     *   <li>OWNER_ADMIN: always yes</li>
     *   <li>AGENCY_ADMIN: yes for all clients in their agency</li>
     *   <li>AGENCY_USER: only if they have the specific permission for this client</li>
     *   <li>CLIENT_USER: only their own client, only view-level permissions</li>
     * </ul>
     */
    public void requireClientPermission(UUID clientId, Permission permission) {
        if (isOwner()) return;
        if (isAgencyAdmin()) return; // Full access to all clients in agency

        if (isClientUser()) {
            UUID userClientId = ctx().getClientId();
            if (userClientId != null && userClientId.equals(clientId)) {
                // Client users only have view access
                if (permission == Permission.CLIENT_VIEW
                        || permission == Permission.CAMPAIGNS_VIEW
                        || permission == Permission.REPORTS_VIEW) {
                    return;
                }
            }
            throw new AccessDeniedException("Client access denied");
        }

        if (isAgencyUser()) {
            boolean has = permissionRepository.existsByUserIdAndClientIdAndPermission(
                    ctx().getUserId(), clientId, permission.name()
            );
            if (!has) {
                throw new AccessDeniedException(
                        "You don't have " + permission + " permission for this client"
                );
            }
            return;
        }

        throw new AccessDeniedException("Access denied");
    }

    /**
     * Get list of client IDs the current user can access.
     * <ul>
     *   <li>OWNER_ADMIN / AGENCY_ADMIN: returns null (meaning ALL clients — caller should not filter)</li>
     *   <li>AGENCY_USER: returns list of assigned client IDs</li>
     *   <li>CLIENT_USER: returns list with single client ID</li>
     * </ul>
     */
    public List<UUID> accessibleClientIds() {
        if (isOwner() || isAgencyAdmin()) return null; // null = all
        if (isClientUser()) {
            UUID clientId = ctx().getClientId();
            return clientId != null ? List.of(clientId) : List.of();
        }
        if (isAgencyUser()) {
            return permissionRepository.findClientIdsByUserId(ctx().getUserId());
        }
        return List.of();
    }

    /**
     * Check if current user has ANY access to a client (at least CLIENT_VIEW).
     */
    public boolean canAccessClient(UUID clientId) {
        try {
            requireClientPermission(clientId, Permission.CLIENT_VIEW);
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }
}
