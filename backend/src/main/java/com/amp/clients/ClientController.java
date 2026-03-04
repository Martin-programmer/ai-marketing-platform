package com.amp.clients;

import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import com.amp.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for client management (API v1).
 */
@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;
    private final ClientProfileService profileService;
    private final AccessControl accessControl;

    public ClientController(ClientService clientService,
                            ClientProfileService profileService,
                            AccessControl accessControl) {
        this.clientService = clientService;
        this.profileService = profileService;
        this.accessControl = accessControl;
    }

    private UUID agencyId() {
        return TenantContextHolder.require().getAgencyId();
    }

    // ---- Client CRUD ----

    @GetMapping
    public ResponseEntity<?> listClients(HttpServletRequest request,
                                         @RequestParam(required = false) UUID agencyId) {
        TenantContext tenant = TenantContextHolder.require();

        // CLIENT_USER: portal only — return their own client
        if ("CLIENT_USER".equals(tenant.getRole())) {
            if (tenant.getClientId() == null) {
                return ResponseEntity.ok(List.of());
            }
            Client client = clientService.getClient(tenant.getAgencyId(), tenant.getClientId());
            return ResponseEntity.ok(List.of(ClientResponse.from(client)));
        }

        // Resolve agency for the query
        UUID targetAgencyId = tenant.getAgencyId();
        if (targetAgencyId == null && accessControl.isOwner()) {
            targetAgencyId = agencyId; // OWNER_ADMIN can pass agencyId param
        }
        if (targetAgencyId == null) {
            return ResponseEntity.ok(List.of());
        }

        // AGENCY_USER: only assigned clients
        List<UUID> clientIds = accessControl.accessibleClientIds();
        List<ClientResponse> clients;
        if (clientIds == null) {
            // OWNER_ADMIN or AGENCY_ADMIN — all clients
            clients = clientService.listClients(targetAgencyId)
                    .stream().map(ClientResponse::from).toList();
        } else {
            clients = clientService.listClientsByIds(targetAgencyId, clientIds)
                    .stream().map(ClientResponse::from).toList();
        }
        return ResponseEntity.ok(clients);
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody CreateClientRequest request) {
        RoleGuard.requireAgencyAdmin();
        Client created = clientService.createClient(agencyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ClientResponse.from(created));
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<ClientResponse> getClient(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CLIENT_VIEW);
        Client client = clientService.getClient(agencyId(), clientId);
        return ResponseEntity.ok(ClientResponse.from(client));
    }

    @PatchMapping("/{clientId}")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable UUID clientId,
            @Valid @RequestBody UpdateClientRequest request) {
        accessControl.requireClientPermission(clientId, Permission.CLIENT_EDIT);
        Client updated = clientService.updateClient(agencyId(), clientId, request);
        return ResponseEntity.ok(ClientResponse.from(updated));
    }

    @PostMapping("/{clientId}/pause")
    public ResponseEntity<ClientResponse> pauseClient(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyAdmin();
        Client paused = clientService.pauseClient(agencyId(), clientId);
        return ResponseEntity.ok(ClientResponse.from(paused));
    }

    @PostMapping("/{clientId}/activate")
    public ResponseEntity<ClientResponse> activateClient(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyAdmin();
        Client activated = clientService.activateClient(agencyId(), clientId);
        return ResponseEntity.ok(ClientResponse.from(activated));
    }

    // ---- Client Profile ----

    @GetMapping("/{clientId}/profile")
    public ResponseEntity<ClientProfileResponse> getProfile(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CLIENT_VIEW);
        ClientProfile profile = profileService.getProfile(clientId);
        return ResponseEntity.ok(ClientProfileResponse.from(profile));
    }

    @PutMapping("/{clientId}/profile")
    public ResponseEntity<ClientProfileResponse> upsertProfile(
            @PathVariable UUID clientId,
            @Valid @RequestBody ClientProfileRequest request) {
        accessControl.requireClientPermission(clientId, Permission.CLIENT_EDIT);
        ClientProfile profile = profileService.upsertProfile(agencyId(), clientId, request);
        return ResponseEntity.ok(ClientProfileResponse.from(profile));
    }
}
