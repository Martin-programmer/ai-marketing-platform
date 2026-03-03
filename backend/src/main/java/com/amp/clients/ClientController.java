package com.amp.clients;

import com.amp.tenancy.TenantContextHolder;
import com.amp.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for client management (API v1).
 */
@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private static final Set<String> ADMIN_ROLES = Set.of("AGENCY_ADMIN", "OWNER_ADMIN");

    private final ClientService clientService;
    private final ClientProfileService profileService;
    private final ClientPermissionService permissionService;

    public ClientController(ClientService clientService,
                            ClientProfileService profileService,
                            ClientPermissionService permissionService) {
        this.clientService = clientService;
        this.profileService = profileService;
        this.permissionService = permissionService;
    }

    private UUID agencyId() {
        return TenantContextHolder.require().getAgencyId();
    }

    private UUID resolveAgencyId(HttpServletRequest request, UUID requestedAgencyId) {
        Object currentAgency = request.getAttribute("currentAgencyId");
        if (currentAgency instanceof UUID agency) {
            return agency;
        }
        TenantContext tenant = null;
        try {
            tenant = TenantContextHolder.get();
        } catch (Exception ignored) {
            // no-op
        }
        if (tenant != null && tenant.getAgencyId() != null) {
            return tenant.getAgencyId();
        }
        Object role = request.getAttribute("currentUserRole");
        if ("OWNER_ADMIN".equals(role)) {
            return requestedAgencyId;
        }
        return null;
    }

    // ---- Client CRUD ----

    @GetMapping
    public ResponseEntity<?> listClients(HttpServletRequest request,
                                         @org.springframework.web.bind.annotation.RequestParam(required = false) UUID agencyId) {
        UUID targetAgencyId = resolveAgencyId(request, agencyId);
        if (targetAgencyId == null) {
            return ResponseEntity.ok(List.of());
        }

        List<ClientResponse> clients = clientService.listClients(targetAgencyId)
                .stream()
                .map(ClientResponse::from)
                .toList();
        return ResponseEntity.ok(clients);
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody CreateClientRequest request) {

        Client created = clientService.createClient(agencyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ClientResponse.from(created));
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<ClientResponse> getClient(@PathVariable UUID clientId) {
        Client client = clientService.getClient(agencyId(), clientId);
        return ResponseEntity.ok(ClientResponse.from(client));
    }

    @PatchMapping("/{clientId}")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable UUID clientId,
            @Valid @RequestBody UpdateClientRequest request) {

        Client updated = clientService.updateClient(agencyId(), clientId, request);
        return ResponseEntity.ok(ClientResponse.from(updated));
    }

    @PostMapping("/{clientId}/pause")
    public ResponseEntity<ClientResponse> pauseClient(@PathVariable UUID clientId) {
        Client paused = clientService.pauseClient(agencyId(), clientId);
        return ResponseEntity.ok(ClientResponse.from(paused));
    }

    @PostMapping("/{clientId}/activate")
    public ResponseEntity<ClientResponse> activateClient(@PathVariable UUID clientId) {
        Client activated = clientService.activateClient(agencyId(), clientId);
        return ResponseEntity.ok(ClientResponse.from(activated));
    }

    // ---- Client Profile ----

    @GetMapping("/{clientId}/profile")
    public ResponseEntity<ClientProfileResponse> getProfile(@PathVariable UUID clientId) {
        ClientProfile profile = profileService.getProfile(clientId);
        return ResponseEntity.ok(ClientProfileResponse.from(profile));
    }

    @PutMapping("/{clientId}/profile")
    public ResponseEntity<ClientProfileResponse> upsertProfile(
            @PathVariable UUID clientId,
            @Valid @RequestBody ClientProfileRequest request) {

        ClientProfile profile = profileService.upsertProfile(agencyId(), clientId, request);
        return ResponseEntity.ok(ClientProfileResponse.from(profile));
    }

    // ---- Client Permissions ----

    @GetMapping("/{clientId}/permissions")
    public ResponseEntity<?> listPermissions(@PathVariable UUID clientId) {
        return ResponseEntity.ok(permissionService.listPermissions(clientId));
    }

    @PutMapping("/{clientId}/permissions")
    public ResponseEntity<?> replacePermissions(
            @PathVariable UUID clientId,
            @RequestBody List<AddPermissionRequest> requests,
            HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Agency Admin access required"));
        }
        try {
            return ResponseEntity.ok(permissionService.replacePermissions(clientId, requests));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REQUEST", "message", e.getMessage()));
        }
    }

    @PostMapping("/{clientId}/permissions/add")
    public ResponseEntity<?> addPermission(
            @PathVariable UUID clientId,
            @Valid @RequestBody AddPermissionRequest req,
            HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Agency Admin access required"));
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(permissionService.addPermission(clientId, req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REQUEST", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{clientId}/permissions/{userId}")
    public ResponseEntity<?> removePermission(
            @PathVariable UUID clientId,
            @PathVariable UUID userId,
            HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Agency Admin access required"));
        }
        permissionService.removeUserPermissions(clientId, userId);
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("currentUserRole");
        return ADMIN_ROLES.contains(role);
    }
}
