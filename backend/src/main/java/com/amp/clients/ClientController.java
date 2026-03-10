package com.amp.clients;

import com.amp.ai.AudienceArchitectService;
import com.amp.ai.ClientBrieferService;
import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import com.amp.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
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
    private final ClientBrieferService brieferService;
    private final AudienceArchitectService audienceService;

    public ClientController(ClientService clientService,
                            ClientProfileService profileService,
                            AccessControl accessControl,
                            ClientBrieferService brieferService,
                            AudienceArchitectService audienceService) {
        this.clientService = clientService;
        this.profileService = profileService;
        this.accessControl = accessControl;
        this.brieferService = brieferService;
        this.audienceService = audienceService;
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

    // ---- AI Client Briefer ----

    /**
     * Trigger AI website analysis to auto-fill client profile.
     */
    @PostMapping("/{clientId}/ai-brief")
    public ResponseEntity<Map<String, Object>> analyzeWebsite(
            @PathVariable UUID clientId,
            @RequestBody Map<String, String> request) {
        accessControl.requireClientPermission(clientId, Permission.CLIENT_EDIT);
        String websiteUrl = request.get("websiteUrl");
        if (websiteUrl == null || websiteUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "websiteUrl is required"));
        }
        Map<String, Object> result = brieferService.analyzeWebsite(agencyId(), clientId, websiteUrl);
        return ResponseEntity.ok(result);
    }

    /**
     * Get last AI analysis result for a client.
     */
    @GetMapping("/{clientId}/ai-brief")
    public ResponseEntity<Map<String, Object>> getLastBrief(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CLIENT_VIEW);
        Map<String, Object> result = brieferService.getLastAnalysis(agencyId(), clientId);
        return ResponseEntity.ok(result);
    }

    // ---- AI Audience Architect ----

    /**
     * Generate AI audience targeting suggestions based on client profile and performance.
     */
    @PostMapping("/{clientId}/ai-audiences")
    public ResponseEntity<Map<String, Object>> suggestAudiences(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_EDIT);
        Map<String, Object> result = audienceService.suggestAudiences(agencyId(), clientId);
        return ResponseEntity.ok(result);
    }
}
