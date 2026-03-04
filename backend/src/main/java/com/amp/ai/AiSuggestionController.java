package com.amp.ai;

import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
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
    private final AccessControl accessControl;

    public AiSuggestionController(AiSuggestionService aiSuggestionService,
                                  AccessControl accessControl) {
        this.aiSuggestionService = aiSuggestionService;
        this.accessControl = accessControl;
    }

    @GetMapping("/clients/{clientId}/suggestions")
    public List<SuggestionResponse> listSuggestions(
            @PathVariable UUID clientId,
            @RequestParam(required = false) String status) {
        accessControl.requireClientPermission(clientId, Permission.AI_VIEW);
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return aiSuggestionService.listSuggestions(agencyId, clientId, status);
    }

    @GetMapping("/suggestions/{suggestionId}")
    public SuggestionResponse getSuggestion(@PathVariable UUID suggestionId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = aiSuggestionService.resolveClientId(agencyId, suggestionId);
        accessControl.requireClientPermission(clientId, Permission.AI_VIEW);
        return aiSuggestionService.getSuggestion(agencyId, suggestionId);
    }

    @PatchMapping("/suggestions/{suggestionId}")
    public SuggestionResponse updateSuggestion(
            @PathVariable UUID suggestionId,
            @RequestBody UpdateSuggestionRequest request) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = aiSuggestionService.resolveClientId(agencyId, suggestionId);
        accessControl.requireClientPermission(clientId, Permission.AI_APPROVE);
        return aiSuggestionService.updateSuggestion(agencyId, suggestionId, request);
    }

    @PostMapping("/suggestions/{suggestionId}/approve")
    public SuggestionResponse approveSuggestion(@PathVariable UUID suggestionId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = aiSuggestionService.resolveClientId(agencyId, suggestionId);
        accessControl.requireClientPermission(clientId, Permission.AI_APPROVE);
        return aiSuggestionService.approveSuggestion(agencyId, suggestionId);
    }

    @PostMapping("/suggestions/{suggestionId}/reject")
    public SuggestionResponse rejectSuggestion(@PathVariable UUID suggestionId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = aiSuggestionService.resolveClientId(agencyId, suggestionId);
        accessControl.requireClientPermission(clientId, Permission.AI_APPROVE);
        return aiSuggestionService.rejectSuggestion(agencyId, suggestionId);
    }

    @PostMapping("/suggestions/{suggestionId}/apply")
    public SuggestionResponse applySuggestion(@PathVariable UUID suggestionId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = aiSuggestionService.resolveClientId(agencyId, suggestionId);
        accessControl.requireClientPermission(clientId, Permission.AI_APPROVE);
        return aiSuggestionService.applySuggestion(agencyId, suggestionId);
    }

    @GetMapping("/suggestions/{suggestionId}/actions")
    public List<ActionLogResponse> getActionLogs(@PathVariable UUID suggestionId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = aiSuggestionService.resolveClientId(agencyId, suggestionId);
        accessControl.requireClientPermission(clientId, Permission.AI_VIEW);
        return aiSuggestionService.getActionLogs(suggestionId);
    }
}
