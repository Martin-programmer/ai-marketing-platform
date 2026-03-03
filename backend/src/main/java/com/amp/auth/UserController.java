package com.amp.auth;

import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for user/team management within an agency.
 * Accessible by AGENCY_ADMIN (and OWNER_ADMIN).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    private static final Set<String> ADMIN_ROLES = Set.of("AGENCY_ADMIN", "OWNER_ADMIN");

    private boolean isAdminRole(HttpServletRequest request) {
        Object role = request.getAttribute("currentUserRole");
        return ADMIN_ROLES.contains(role);
    }

    private UUID agencyId(HttpServletRequest request) {
        Object attr = request.getAttribute("currentAgencyId");
        return attr instanceof UUID ? (UUID) attr : null;
    }

    private UUID currentUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("currentUserId");
        return attr instanceof UUID ? (UUID) attr : null;
    }

    private UUID resolveTargetAgency(HttpServletRequest request, UUID requestedAgencyId) {
        UUID currentAgency = agencyId(request);
        if (currentAgency != null) {
            return currentAgency;
        }
        TenantContext tenant = TenantContextHolder.get();
        if (tenant != null && tenant.getAgencyId() != null) {
            return tenant.getAgencyId();
        }
        Object role = request.getAttribute("currentUserRole");
        if ("OWNER_ADMIN".equals(role)) {
            return requestedAgencyId;
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<?> listUsers(HttpServletRequest request,
                                       @RequestParam(required = false) UUID agencyId) {
        if (!isAdminRole(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Agency Admin access required"));
        }
        UUID agency = resolveTargetAgency(request, agencyId);
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required. OWNER_ADMIN must pass agencyId."));
        }
        List<UserResponse> users = userService.listUsers(agency);
        return ResponseEntity.ok(users);
    }

    @PostMapping("/invite")
    public ResponseEntity<?> inviteUser(@Valid @RequestBody InviteUserRequest req,
                                        HttpServletRequest request,
                                        @RequestParam(required = false) UUID agencyId) {
        if (!isAdminRole(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Agency Admin access required"));
        }
        UUID agency = resolveTargetAgency(request, agencyId);
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required. OWNER_ADMIN must pass agencyId."));
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
                                     HttpServletRequest request,
                                     @RequestParam(required = false) UUID agencyId) {
        UUID agency = resolveTargetAgency(request, agencyId);
        UUID currentUser = currentUserId(request);

        // Allow AGENCY_ADMIN or self
        if (!isAdminRole(request) && !userId.equals(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Access denied"));
        }
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required. OWNER_ADMIN must pass agencyId."));
        }
        UserResponse user = userService.getUser(userId, agency);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<?> updateUser(@PathVariable UUID userId,
                                        @RequestBody UpdateUserRequest req,
                                        HttpServletRequest request,
                                        @RequestParam(required = false) UUID agencyId) {
        if (!isAdminRole(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Agency Admin access required"));
        }
        UUID agency = resolveTargetAgency(request, agencyId);
        UUID currentUser = currentUserId(request);
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required. OWNER_ADMIN must pass agencyId."));
        }
        try {
            UserResponse updated = userService.updateUser(userId, agency, currentUser, req);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REQUEST", "message", e.getMessage()));
        }
    }

    @PostMapping("/{userId}/disable")
    public ResponseEntity<?> disableUser(@PathVariable UUID userId,
                                         HttpServletRequest request,
                                         @RequestParam(required = false) UUID agencyId) {
        if (!isAdminRole(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Agency Admin access required"));
        }
        UUID agency = resolveTargetAgency(request, agencyId);
        UUID currentUser = currentUserId(request);
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required. OWNER_ADMIN must pass agencyId."));
        }
        try {
            UserResponse disabled = userService.disableUser(userId, agency, currentUser);
            return ResponseEntity.ok(disabled);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REQUEST", "message", e.getMessage()));
        }
    }

    @PostMapping("/{userId}/enable")
    public ResponseEntity<?> enableUser(@PathVariable UUID userId,
                                        HttpServletRequest request,
                                        @RequestParam(required = false) UUID agencyId) {
        if (!isAdminRole(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Agency Admin access required"));
        }
        UUID agency = resolveTargetAgency(request, agencyId);
        if (agency == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "MISSING_AGENCY", "message", "Agency context required. OWNER_ADMIN must pass agencyId."));
        }
        UserResponse enabled = userService.enableUser(userId, agency);
        return ResponseEntity.ok(enabled);
    }
}
