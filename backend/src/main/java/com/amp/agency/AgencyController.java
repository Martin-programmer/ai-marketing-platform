package com.amp.agency;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for agency management (Owner Admin).
 */
@RestController
@RequestMapping("/api/v1/admin/agencies")
public class AgencyController {

    private final AgencyService agencyService;

    public AgencyController(AgencyService agencyService) {
        this.agencyService = agencyService;
    }

    @GetMapping
    public List<AgencyResponse> listAgencies() {
        return agencyService.listAgencies();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgencyResponse createAgency(@Valid @RequestBody CreateAgencyRequest req) {
        return agencyService.createAgency(req);
    }

    @GetMapping("/{agencyId}")
    public AgencyResponse getAgency(@PathVariable UUID agencyId) {
        return agencyService.getAgency(agencyId);
    }

    @PatchMapping("/{agencyId}")
    public AgencyResponse updateAgency(@PathVariable UUID agencyId,
                                       @RequestBody UpdateAgencyRequest req) {
        return agencyService.updateAgency(agencyId, req);
    }

    @PostMapping("/{agencyId}/suspend")
    public AgencyResponse suspendAgency(@PathVariable UUID agencyId) {
        return agencyService.suspendAgency(agencyId);
    }

    @PostMapping("/{agencyId}/reactivate")
    public AgencyResponse reactivateAgency(@PathVariable UUID agencyId) {
        return agencyService.reactivateAgency(agencyId);
    }
}
