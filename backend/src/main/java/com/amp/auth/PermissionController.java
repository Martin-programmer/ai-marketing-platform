package com.amp.auth;

import com.amp.clients.ClientPermissionService;
import com.amp.clients.UserClientPermission;
import com.amp.clients.UserClientPermissionRepository;
import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manage granular permissions for AGENCY_USER per client.
 * Accessible by AGENCY_ADMIN and OWNER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final UserClientPermissionRepository permissionRepo;
    private final UserAccountRepository userRepo;
    private final AccessControl accessControl;
    private final ClientPermissionService permissionService;

    public PermissionController(UserClientPermissionRepository permissionRepo,
                                UserAccountRepository userRepo,
                                AccessControl accessControl,
                                ClientPermissionService permissionService) {
        this.permissionRepo = permissionRepo;
        this.userRepo = userRepo;
        this.accessControl = accessControl;
        this.permissionService = permissionService;
    }

    /**
     * Get all permissions for a user across all clients.
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUserPermissions(@PathVariable UUID userId) {
        RoleGuard.requireAgencyAdmin();
        List<UserClientPermission> perms = permissionRepo.findByUserId(userId);

        // Group by clientId
        Map<UUID, List<String>> grouped = perms.stream()
                .collect(Collectors.groupingBy(
                        UserClientPermission::getClientId,
                        Collectors.mapping(UserClientPermission::getPermission, Collectors.toList())
                ));

        return ResponseEntity.ok(grouped);
    }

    /**
     * Get all permissions for a specific client (which users have access).
     */
    @GetMapping("/clients/{clientId}")
    public ResponseEntity<?> getClientPermissions(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyAdmin();
        List<UserClientPermission> perms = permissionRepo.findByClientId(clientId);

        Map<UUID, List<String>> grouped = perms.stream()
                .collect(Collectors.groupingBy(
                        UserClientPermission::getUserId,
                        Collectors.mapping(UserClientPermission::getPermission, Collectors.toList())
                ));

        return ResponseEntity.ok(grouped);
    }

    /**
     * Set permissions for a user on a specific client.
     * Replaces all existing permissions for this user-client pair.
     */
    @PutMapping("/users/{userId}/clients/{clientId}")
    public ResponseEntity<?> setPermissions(
            @PathVariable UUID userId,
            @PathVariable UUID clientId,
            @RequestBody PermissionUpdateRequest request) {
        RoleGuard.requireAgencyAdmin();

        // Validate user exists and is AGENCY_USER
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!"AGENCY_USER".equals(user.getRole())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Permissions can only be set for AGENCY_USER role"));
        }

        UUID grantedBy = TenantContextHolder.require().getUserId();
        permissionService.setUserClientPermissions(userId, clientId, request.permissions(), grantedBy);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "clientId", clientId,
                "permissions", request.permissions()
        ));
    }

    /**
     * Remove all permissions for a user on a specific client.
     */
    @DeleteMapping("/users/{userId}/clients/{clientId}")
    public ResponseEntity<?> removePermissions(
            @PathVariable UUID userId,
            @PathVariable UUID clientId) {
        RoleGuard.requireAgencyAdmin();
        permissionService.removeUserPermissions(clientId, userId);
        return ResponseEntity.ok(Map.of("message", "Permissions removed"));
    }

    /**
     * Bulk assign a permission preset to a user for a client.
     */
    @PostMapping("/users/{userId}/clients/{clientId}/preset")
    public ResponseEntity<?> applyPreset(
            @PathVariable UUID userId,
            @PathVariable UUID clientId,
            @RequestBody Map<String, String> request) {
        RoleGuard.requireAgencyAdmin();

        String preset = request.get("preset");
        Permission[] perms = switch (preset) {
            case "READ_ONLY" -> Permission.readOnly();
            case "EDITOR" -> Permission.editor();
            case "FULL_ACCESS" -> Permission.all();
            default -> throw new IllegalArgumentException(
                    "Unknown preset: " + preset + ". Use READ_ONLY, EDITOR, or FULL_ACCESS");
        };

        UUID grantedBy = TenantContextHolder.require().getUserId();
        List<String> permNames = Arrays.stream(perms).map(Enum::name).toList();
        permissionService.setUserClientPermissions(userId, clientId, permNames, grantedBy);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "clientId", clientId,
                "preset", preset,
                "permissions", Arrays.stream(perms).map(Enum::name).toList()
        ));
    }

    /**
     * Get available permission types and presets.
     */
    @GetMapping("/users/{userId}/clients/{clientId}/effective")
    public ResponseEntity<?> getEffectivePermissions(@PathVariable UUID userId,
                                                      @PathVariable UUID clientId) {
        RoleGuard.requireAgencyAdmin();
        Set<String> effective = accessControl.getEffectivePermissions(userId, clientId);
        List<String> direct = permissionRepo.findPermissionsByUserIdAndClientId(userId, clientId);

        return ResponseEntity.ok(Map.of(
                "directPermissions", direct,
                "effectivePermissions", effective,
                "inheritedPermissions", effective.stream()
                        .filter(p -> !direct.contains(p))
                        .toList()
        ));
    }

    @GetMapping("/available")
    public ResponseEntity<?> availablePermissions() {
        return ResponseEntity.ok(Map.of(
                "permissions", Arrays.stream(Permission.values()).map(p -> Map.of(
                        "name", p.name(),
                        "description", describePermission(p)
                )).toList(),
                "presets", List.of(
                        Map.of("name", "READ_ONLY", "description", "View-only access", "permissions",
                                Arrays.stream(Permission.readOnly()).map(Enum::name).toList()),
                        Map.of("name", "EDITOR", "description", "Can view and edit most things", "permissions",
                                Arrays.stream(Permission.editor()).map(Enum::name).toList()),
                        Map.of("name", "FULL_ACCESS", "description", "All permissions", "permissions",
                                Arrays.stream(Permission.all()).map(Enum::name).toList())
                )
        ));
    }

    private String describePermission(Permission p) {
        return switch (p) {
            case CLIENT_VIEW -> "View client data and profile";
            case CLIENT_EDIT -> "Edit client profile and settings";
            case CAMPAIGNS_VIEW -> "View campaigns and performance data";
            case CAMPAIGNS_EDIT -> "Create and edit campaigns";
            case CAMPAIGNS_PUBLISH -> "Publish campaigns to Meta";
            case CREATIVES_VIEW -> "View creative library";
            case CREATIVES_EDIT -> "Upload and manage creatives";
            case REPORTS_VIEW -> "View reports";
            case REPORTS_EDIT -> "Create and edit reports";
            case REPORTS_SEND -> "Send reports to clients";
            case META_MANAGE -> "Manage Meta integration connection";
            case AI_VIEW -> "View AI suggestions";
            case AI_APPROVE -> "Approve or reject AI suggestions";
        };
    }

    public record PermissionUpdateRequest(List<String> permissions) {}
}
