package com.amp.ai;

import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AiHistoryController {

    private final AiBudgetAnalysisRepository aiBudgetAnalysisRepository;
    private final AiAudienceSuggestionRepository aiAudienceSuggestionRepository;
    private final AccessControl accessControl;
    private final ObjectMapper objectMapper;

    public AiHistoryController(AiBudgetAnalysisRepository aiBudgetAnalysisRepository,
                               AiAudienceSuggestionRepository aiAudienceSuggestionRepository,
                               AccessControl accessControl,
                               ObjectMapper objectMapper) {
        this.aiBudgetAnalysisRepository = aiBudgetAnalysisRepository;
        this.aiAudienceSuggestionRepository = aiAudienceSuggestionRepository;
        this.accessControl = accessControl;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/ai-budget-analyses/{id}")
    public ResponseEntity<AiStoredResultResponse> getBudgetAnalysis(@PathVariable UUID id) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        AiBudgetAnalysis analysis = aiBudgetAnalysisRepository.findByIdAndAgencyId(id, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("AiBudgetAnalysis", id));
        accessControl.requireClientPermission(analysis.getClientId(), Permission.CAMPAIGNS_VIEW);
        Map<String, Object> data = readJsonMap(analysis.getAnalysisJson());
        String preview = previewText(String.valueOf(data.getOrDefault("narrative", "Budget analysis")));
        return ResponseEntity.ok(new AiStoredResultResponse(analysis.getId(), analysis.getCreatedAt(), preview, data));
    }

    @GetMapping("/ai-audiences/{id}")
    public ResponseEntity<AiStoredResultResponse> getAudienceSuggestion(@PathVariable UUID id) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        AiAudienceSuggestion suggestion = aiAudienceSuggestionRepository.findByIdAndAgencyId(id, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("AiAudienceSuggestion", id));
        accessControl.requireClientPermission(suggestion.getClientId(), Permission.CAMPAIGNS_VIEW);
        Map<String, Object> data = readJsonMap(suggestion.getSuggestionJson());
        String preview = "Audience suggestion";
        Object rawAudiences = data.get("recommended_audiences");
        if (rawAudiences instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object name = first.get("name");
            if (name != null && !String.valueOf(name).isBlank()) {
                preview = String.valueOf(name);
            }
        }
        return ResponseEntity.ok(new AiStoredResultResponse(suggestion.getId(), suggestion.getCreatedAt(), previewText(preview), data));
    }

    private Map<String, Object> readJsonMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("error", "Failed to parse saved AI response");
        }
    }

    private String previewText(String text) {
        if (text == null) return "";
        String trimmed = text.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100) + "...";
    }
}
