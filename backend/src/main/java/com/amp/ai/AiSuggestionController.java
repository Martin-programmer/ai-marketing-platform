package com.amp.ai;

import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for AI suggestion lifecycle operations.
 */
@RestController
@RequestMapping("/api/v1")
public class AiSuggestionController {

    private final AiSuggestionService aiSuggestionService;

    public AiSuggestionController(AiSuggestionService aiSuggestionService) {
        this.aiSuggestionService = aiSuggestionService;
    }

    @GetMapping("/clients/{clientId}/suggestions")
    public List<SuggestionResponse> listSuggestions(
            @PathVariable UUID clientId,
            @RequestParam(required = false) String status) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return aiSuggestionService.listSuggestions(agencyId, clientId, status);
    }

    @GetMapping("/suggestions/{suggestionId}")
    public SuggestionResponse getSuggestion(@PathVariable UUID suggestionId) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return aiSuggestionService.getSuggestion(agencyId, suggestionId);
    }

    @PatchMapping("/suggestions/{suggestionId}")
    public SuggestionResponse updateSuggestion(
            @PathVariable UUID suggestionId,
            @RequestBody UpdateSuggestionRequest request) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return aiSuggestionService.updateSuggestion(agencyId, suggestionId, request);
    }

    @PostMapping("/suggestions/{suggestionId}/approve")
    public SuggestionResponse approveSuggestion(@PathVariable UUID suggestionId) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return aiSuggestionService.approveSuggestion(agencyId, suggestionId);
    }

    @PostMapping("/suggestions/{suggestionId}/reject")
    public SuggestionResponse rejectSuggestion(@PathVariable UUID suggestionId) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return aiSuggestionService.rejectSuggestion(agencyId, suggestionId);
    }

    @PostMapping("/suggestions/{suggestionId}/apply")
    public SuggestionResponse applySuggestion(@PathVariable UUID suggestionId) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return aiSuggestionService.applySuggestion(agencyId, suggestionId);
    }

    @GetMapping("/suggestions/{suggestionId}/actions")
    public List<ActionLogResponse> getActionLogs(@PathVariable UUID suggestionId) {
        RoleGuard.requireAgencyRole();
        return aiSuggestionService.getActionLogs(suggestionId);
    }
}
