package com.amp.auth;

import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for user/team management within an agency.
 * OWNER_ADMIN can manage users in any agency (must pass agencyId).
 * AGENCY_ADMIN can manage users in their own agency.
 * AGENCY_USER can only view their own profile.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AccessControl accessControl;

    public UserController(UserService userService, AccessControl accessControl) {
        this.userService = userService;
        this.accessControl = accessControl;
    }

    private UUID currentUserId() {
        return TenantContextHolder.require().getUserId();
    }

    /**
     * Resolves the target agency. AGENCY_ADMIN uses their own agency from tenant context.
     * OWNER_ADMIN must provide an explicit agencyId parameter.
     */
    private UUID resolveTargetAgency(UUID requestedAgencyId) {
        if (accessControl.isOwner()) {
            if (requestedAgencyId == null) {
                throw new IllegalArgumentException("OWNER_ADMIN must pass agencyId query parameter");
            }
            return requestedAgencyId;
        }
        TenantContext tenant = TenantContextHolder.get();
        if (tenant != null && tenant.getAgencyId() != null) {
            return tenant.getAgencyId();
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<?> listUsers(@RequestParam(required = false) UUID agencyId) {
        RoleGuard.requireAgencyAdmin();
        UUID agency = resolveTargetAgency(agencyId);
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required."));
        }
        List<UserResponse> users = userService.listUsers(agency);
        return ResponseEntity.ok(users);
    }

    @PostMapping("/invite")
    public ResponseEntity<?> inviteUser(@Valid @RequestBody InviteUserRequest req,
                                        @RequestParam(required = false) UUID agencyId) {
        RoleGuard.requireAgencyAdmin();
        UUID agency = resolveTargetAgency(agencyId);
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required."));
        }
        try {
            UserResponse created = userService.inviteUser(agency, req);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REQUEST", "message", e.getMessage()));
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUser(@PathVariable UUID userId,
                                     @RequestParam(required = false) UUID agencyId) {
        // Allow admin or self-access
        boolean isSelf = userId.equals(currentUserId());
        if (!isSelf) {
            RoleGuard.requireAgencyAdmin();
        }
        UUID agency = resolveTargetAgency(agencyId);
        if (agency == null) {
            // For self-access by non-admin, use tenant context
            TenantContext tenant = TenantContextHolder.get();
            agency = tenant != null ? tenant.getAgencyId() : null;
        }
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required."));
        }
        UserResponse user = userService.getUser(userId, agency);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<?> updateUser(@PathVariable UUID userId,
                                        @RequestBody UpdateUserRequest req,
                                        @RequestParam(required = false) UUID agencyId) {
        RoleGuard.requireAgencyAdmin();
        UUID agency = resolveTargetAgency(agencyId);
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required."));
        }
        try {
            UserResponse updated = userService.updateUser(userId, agency, currentUserId(), req);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REQUEST", "message", e.getMessage()));
        }
    }

    @PostMapping("/{userId}/disable")
    public ResponseEntity<?> disableUser(@PathVariable UUID userId,
                                         @RequestParam(required = false) UUID agencyId) {
        RoleGuard.requireAgencyAdmin();
        UUID agency = resolveTargetAgency(agencyId);
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required."));
        }
        try {
            UserResponse disabled = userService.disableUser(userId, agency, currentUserId());
            return ResponseEntity.ok(disabled);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REQUEST", "message", e.getMessage()));
        }
    }

    @PostMapping("/{userId}/enable")
    public ResponseEntity<?> enableUser(@PathVariable UUID userId,
                                        @RequestParam(required = false) UUID agencyId) {
        RoleGuard.requireAgencyAdmin();
        UUID agency = resolveTargetAgency(agencyId);
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required."));
        }
        UserResponse enabled = userService.enableUser(userId, agency);
        return ResponseEntity.ok(enabled);
    }
}
