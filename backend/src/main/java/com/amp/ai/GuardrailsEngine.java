package com.amp.ai;

import com.amp.config.SystemSettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filters raw rule findings through safety guardrails before they become suggestions.
 */
@Component
public class GuardrailsEngine {

    private static final Logger log = LoggerFactory.getLogger(GuardrailsEngine.class);

    private final AiProperties aiProps;
    private final AiSuggestionRepository suggestionRepo;
    private final SystemSettingService systemSettingService;
    private final ObjectMapper objectMapper;

    public GuardrailsEngine(AiProperties aiProps, AiSuggestionRepository suggestionRepo,
                            SystemSettingService systemSettingService) {
        this.aiProps = aiProps;
        this.suggestionRepo = suggestionRepo;
        this.systemSettingService = systemSettingService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if a finding passes all guardrails.
     * Returns null if passes, or a rejection reason string if blocked.
     */
    public String checkGuardrails(RuleFinding finding, UUID agencyId, UUID clientId) {
        // Read thresholds from DB with AiProperties fallback
        int minDataDays = systemSettingService.getInt("optimizer.min.data.days",
                aiProps.getOptimizer().getMinDataDays());
        int minConversions = systemSettingService.getInt("optimizer.min.conversions",
                aiProps.getOptimizer().getMinConversions());
        int budgetCumulativeMaxPercent = systemSettingService.getInt("optimizer.budget.cumulative.max.percent",
                aiProps.getOptimizer().getBudgetCumulativeMaxPercent());

        // 1. Minimum data window check
        if (finding.requiresMinData()) {
            if (finding.dataDays() < minDataDays) {
                return "Insufficient data: " + finding.dataDays() + " days (min: "
                       + minDataDays + ")";
            }
            if (finding.totalConversions() < minConversions) {
                return "Insufficient conversions: " + finding.totalConversions() + " (min: "
                       + minConversions + ")";
            }
        }

        // 2. Cooldown check
        int cooldownHours = getCooldownHours(finding.suggestionType());
        if (cooldownHours > 0) {
            OffsetDateTime cooldownSince = OffsetDateTime.now().minusHours(cooldownHours);
            List<AiSuggestion> recent = suggestionRepo
                    .findByAgencyIdAndClientIdAndScopeTypeAndScopeIdAndSuggestionTypeAndCreatedAtAfter(
                            agencyId, clientId, finding.scopeType(), finding.scopeId(),
                            finding.suggestionType(), cooldownSince);

            if (!recent.isEmpty()) {
                return "Cooldown active: " + finding.suggestionType() + " for " + finding.scopeId()
                       + " (last: " + recent.get(0).getCreatedAt() + ", cooldown: " + cooldownHours + "h)";
            }
        }

        // 3. Deduplication — max 1 suggestion of same type for same entity in 7 days
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        List<AiSuggestion> duplicates = suggestionRepo
                .findByAgencyIdAndClientIdAndScopeTypeAndScopeIdAndSuggestionTypeAndCreatedAtAfter(
                        agencyId, clientId, finding.scopeType(), finding.scopeId(),
                        finding.suggestionType(), sevenDaysAgo);
        if (!duplicates.isEmpty()) {
            return "Duplicate: already have " + finding.suggestionType()
                   + " for " + finding.scopeId() + " within 7 days";
        }

        // 4. Budget cumulative limit check
        if ("BUDGET_ADJUST".equals(finding.suggestionType())) {
            List<AiSuggestion> recentBudgetChanges = suggestionRepo
                    .findByAgencyIdAndClientIdAndScopeIdAndSuggestionTypeAndStatusInAndCreatedAtAfter(
                            agencyId, clientId, finding.scopeId(), "BUDGET_ADJUST",
                            List.of("APPROVED", "APPLIED"), sevenDaysAgo);

            double cumulativeChange = recentBudgetChanges.stream()
                    .mapToDouble(s -> extractChangePercent(s.getPayloadJson()))
                    .sum();

            if (Math.abs(cumulativeChange + finding.changePercent())
                    > budgetCumulativeMaxPercent) {
                return "Cumulative budget change would exceed "
                       + budgetCumulativeMaxPercent
                       + "% (current: " + cumulativeChange + "%, proposed: " + finding.changePercent() + "%)";
            }
        }

        return null; // All checks passed
    }

    private int getCooldownHours(String suggestionType) {
        return switch (suggestionType) {
            case "BUDGET_ADJUST" -> systemSettingService.getInt("optimizer.cooldown.budget.hours",
                    aiProps.getOptimizer().getCooldownBudgetHours());
            case "PAUSE" -> systemSettingService.getInt("optimizer.cooldown.pause.hours",
                    aiProps.getOptimizer().getCooldownPauseHours());
            default -> 0;
        };
    }

    private double extractChangePercent(String payloadJson) {
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            return payload.has("change_percent") ? payload.get("change_percent").asDouble() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
