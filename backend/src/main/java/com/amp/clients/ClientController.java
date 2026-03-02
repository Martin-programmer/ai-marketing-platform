package com.amp.clients;

import com.amp.tenancy.TenantContextHolder;
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

    public ClientController(ClientService clientService,
                            ClientProfileService profileService) {
        this.clientService = clientService;
        this.profileService = profileService;
    }

    private UUID agencyId() {
        return TenantContextHolder.require().getAgencyId();
    }

    // ---- Client CRUD ----

    @GetMapping
    public ResponseEntity<List<ClientResponse>> listClients() {
        List<ClientResponse> clients = clientService.listClients(agencyId())
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
}
