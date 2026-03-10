package com.amp.ai;

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
    private final ObjectMapper objectMapper;

    public GuardrailsEngine(AiProperties aiProps, AiSuggestionRepository suggestionRepo) {
        this.aiProps = aiProps;
        this.suggestionRepo = suggestionRepo;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if a finding passes all guardrails.
     * Returns null if passes, or a rejection reason string if blocked.
     */
    public String checkGuardrails(RuleFinding finding, UUID agencyId, UUID clientId) {
        // 1. Minimum data window check
        if (finding.requiresMinData()) {
            if (finding.dataDays() < aiProps.getOptimizer().getMinDataDays()) {
                return "Insufficient data: " + finding.dataDays() + " days (min: "
                       + aiProps.getOptimizer().getMinDataDays() + ")";
            }
            if (finding.totalConversions() < aiProps.getOptimizer().getMinConversions()) {
                return "Insufficient conversions: " + finding.totalConversions() + " (min: "
                       + aiProps.getOptimizer().getMinConversions() + ")";
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
                    > aiProps.getOptimizer().getBudgetCumulativeMaxPercent()) {
                return "Cumulative budget change would exceed "
                       + aiProps.getOptimizer().getBudgetCumulativeMaxPercent()
                       + "% (current: " + cumulativeChange + "%, proposed: " + finding.changePercent() + "%)";
            }
        }

        return null; // All checks passed
    }

    private int getCooldownHours(String suggestionType) {
        return switch (suggestionType) {
            case "BUDGET_ADJUST" -> aiProps.getOptimizer().getCooldownBudgetHours();
            case "PAUSE" -> aiProps.getOptimizer().getCooldownPauseHours();
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
