package com.amp.clients;

import com.amp.ai.AnomalyDetectorService;
import com.amp.ai.AiAudienceSuggestion;
import com.amp.ai.AiAudienceSuggestionRepository;
import com.amp.ai.AiBudgetAnalysis;
import com.amp.ai.AiBudgetAnalysisRepository;
import com.amp.ai.AiStoredResultResponse;
import com.amp.ai.AudienceArchitectService;
import com.amp.ai.BudgetStrategistService;
import com.amp.ai.ClientBrieferService;
import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import com.amp.tenancy.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final BudgetStrategistService budgetStrategist;
    private final AnomalyDetectorService anomalyDetector;
    private final AiAudienceSuggestionRepository aiAudienceSuggestionRepository;
    private final AiBudgetAnalysisRepository aiBudgetAnalysisRepository;
    private final ObjectMapper objectMapper;

    public ClientController(ClientService clientService,
                            ClientProfileService profileService,
                            AccessControl accessControl,
                            ClientBrieferService brieferService,
                            AudienceArchitectService audienceService,
                            BudgetStrategistService budgetStrategist,
                            AnomalyDetectorService anomalyDetector,
                            AiAudienceSuggestionRepository aiAudienceSuggestionRepository,
                            AiBudgetAnalysisRepository aiBudgetAnalysisRepository,
                            ObjectMapper objectMapper) {
        this.clientService = clientService;
        this.profileService = profileService;
        this.accessControl = accessControl;
        this.brieferService = brieferService;
        this.audienceService = audienceService;
        this.budgetStrategist = budgetStrategist;
        this.anomalyDetector = anomalyDetector;
        this.aiAudienceSuggestionRepository = aiAudienceSuggestionRepository;
        this.aiBudgetAnalysisRepository = aiBudgetAnalysisRepository;
        this.objectMapper = objectMapper;
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

    // ---- Client Questionnaire ----

    @PutMapping("/{clientId}/questionnaire")
    public ResponseEntity<Map<String, Object>> saveQuestionnaire(
            @PathVariable UUID clientId,
            @RequestBody ClientQuestionnaireRequest request,
            @RequestParam(defaultValue = "false") boolean complete) {
        accessControl.requireClientPermission(clientId, Permission.CLIENT_EDIT);
        profileService.saveQuestionnaire(agencyId(), clientId, request, complete);
        Map<String, Object> result = profileService.getQuestionnaire(clientId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{clientId}/questionnaire")
    public ResponseEntity<Map<String, Object>> getQuestionnaire(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CLIENT_VIEW);
        Map<String, Object> result = profileService.getQuestionnaire(clientId);
        return ResponseEntity.ok(result);
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
        try {
            AiAudienceSuggestion result = audienceService.suggestAudiences(agencyId(), clientId);
            return ResponseEntity.ok(readJsonMap(result.getSuggestionJson()));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    // ---- AI Budget Strategist ----

    /**
     * Advanced budget analysis: pacing, day-of-week, campaign ranking, diminishing returns.
     */
    @GetMapping("/{clientId}/ai-budget-analysis")
    public ResponseEntity<Map<String, Object>> analyzeBudget(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_VIEW);
        try {
            AiBudgetAnalysis result = budgetStrategist.analyzeBudget(agencyId(), clientId);
            return ResponseEntity.ok(readJsonMap(result.getAnalysisJson()));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{clientId}/ai-budget-analyses")
    public ResponseEntity<List<AiStoredResultResponse>> listBudgetAnalyses(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_VIEW);
        List<AiStoredResultResponse> result = aiBudgetAnalysisRepository
                .findTop20ByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId(), clientId)
                .stream()
                .map(this::toBudgetAnalysisResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{clientId}/ai-audiences/history")
    public ResponseEntity<List<AiStoredResultResponse>> listAudienceSuggestionHistory(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_VIEW);
        List<AiStoredResultResponse> result = aiAudienceSuggestionRepository
                .findTop20ByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId(), clientId)
                .stream()
                .map(this::toAudienceSuggestionResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ---- AI Anomaly Detector ----

    /**
     * Manually trigger anomaly detection for a client.
     */
    @PostMapping("/{clientId}/ai-anomaly-check")
    public ResponseEntity<Map<String, Object>> checkAnomalies(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_VIEW);
        Map<String, Object> result = anomalyDetector.detectAnomalies(agencyId(), clientId);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> readJsonMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("error", "Failed to parse saved AI response");
        }
    }

    private AiStoredResultResponse toBudgetAnalysisResponse(AiBudgetAnalysis analysis) {
        Map<String, Object> data = readJsonMap(analysis.getAnalysisJson());
        String preview = previewText(String.valueOf(data.getOrDefault("narrative", "Budget analysis")));
        return new AiStoredResultResponse(analysis.getId(), analysis.getCreatedAt(), preview, data);
    }

    private AiStoredResultResponse toAudienceSuggestionResponse(AiAudienceSuggestion suggestion) {
        Map<String, Object> data = readJsonMap(suggestion.getSuggestionJson());
        Object rawAudiences = data.get("recommended_audiences");
        String preview = "Audience suggestion";
        if (rawAudiences instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object name = first.get("name");
            if (name != null && !String.valueOf(name).isBlank()) {
                preview = String.valueOf(name);
            }
        }
        return new AiStoredResultResponse(suggestion.getId(), suggestion.getCreatedAt(), previewText(preview), data);
    }

    private String previewText(String text) {
        if (text == null) return "";
        String trimmed = text.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100) + "...";
    }
}
