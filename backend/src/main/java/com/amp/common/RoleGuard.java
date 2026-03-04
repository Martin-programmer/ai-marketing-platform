package com.amp.common;

import com.amp.tenancy.TenantContextHolder;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

/**
 * Utility class for checking role-based access within controller methods.
 * <p>
 * Agency endpoints call {@link #requireAgencyRole()} to block
 * {@code CLIENT_USER} access, directing them to the portal API instead.
 */
public final class RoleGuard {

    private static final Set<String> AGENCY_ROLES =
            Set.of("OWNER_ADMIN", "AGENCY_ADMIN", "AGENCY_USER");

    private RoleGuard() {
        // utility class
    }

    /**
     * Throws {@link AccessDeniedException} if the current user does not
     * hold an agency-level role (OWNER_ADMIN, AGENCY_ADMIN, AGENCY_USER).
     */
    public static void requireAgencyRole() {
        String role = TenantContextHolder.require().getRole();
        if (!AGENCY_ROLES.contains(role)) {
            throw new AccessDeniedException(
                    "This endpoint requires an agency role. Use /api/v1/portal/* for client access.");
        }
    }

    /**
     * Requires OWNER_ADMIN role (platform superadmin).
     */
    public static void requireOwnerAdmin() {
        String role = TenantContextHolder.require().getRole();
        if (!"OWNER_ADMIN".equals(role)) {
            throw new AccessDeniedException("Owner Admin access required");
        }
    }

    /**
     * Requires AGENCY_ADMIN or OWNER_ADMIN role.
     */
    public static void requireAgencyAdmin() {
        String role = TenantContextHolder.require().getRole();
        if (!Set.of("OWNER_ADMIN", "AGENCY_ADMIN").contains(role)) {
            throw new AccessDeniedException("Agency Admin access required");
        }
    }
}
