package com.amp.meta;

import com.amp.tenancy.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Meta platform integration.
 */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

    private final MetaService metaService;

    public MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    @PostMapping("/clients/{clientId}/meta/connect/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectStartResponse connectStart(@PathVariable UUID clientId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return metaService.connectStart(agencyId, clientId);
    }

    @GetMapping("/clients/{clientId}/meta/connection")
    public MetaConnectionResponse getConnection(@PathVariable UUID clientId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return metaService.getConnection(agencyId, clientId);
    }

    @PostMapping("/clients/{clientId}/meta/disconnect")
    public void disconnect(@PathVariable UUID clientId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        metaService.disconnect(agencyId, clientId);
    }

    @GetMapping("/clients/{clientId}/meta/sync/status")
    public MetaSyncJobResponse getSyncStatus(@PathVariable UUID clientId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return metaService.getSyncStatus(agencyId, clientId);
    }

    @PostMapping("/clients/{clientId}/meta/sync/initial")
    @ResponseStatus(HttpStatus.CREATED)
    public MetaSyncJobResponse triggerInitialSync(@PathVariable UUID clientId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return metaService.triggerSync(agencyId, clientId, "INITIAL");
    }

    @PostMapping("/clients/{clientId}/meta/sync/daily")
    @ResponseStatus(HttpStatus.CREATED)
    public MetaSyncJobResponse triggerDailySync(@PathVariable UUID clientId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return metaService.triggerSync(agencyId, clientId, "DAILY");
    }

    @PostMapping("/clients/{clientId}/meta/sync/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public MetaSyncJobResponse triggerManualSync(@PathVariable UUID clientId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return metaService.triggerSync(agencyId, clientId, "MANUAL");
    }
}
