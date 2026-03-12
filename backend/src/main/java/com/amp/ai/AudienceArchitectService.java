package com.amp.ai;

import com.amp.ai.ClaudeApiClient.ClaudeResponse;
import com.amp.campaigns.Adset;
import com.amp.campaigns.AdsetRepository;
import com.amp.campaigns.Campaign;
import com.amp.campaigns.CampaignRepository;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Suggests Meta Ads audience targeting strategies based on client profile
 * and historical performance data.
 */
@Service
public class AudienceArchitectService {

    private static final Logger log = LoggerFactory.getLogger(AudienceArchitectService.class);
    private static final String MODULE = "AUDIENCE_ARCHITECT";

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;
    private final CampaignRepository campaignRepo;
    private final AdsetRepository adsetRepo;
    private final InsightDailyRepository insightRepo;
    private final AiContextBuilder aiContextBuilder;
    private final AiAudienceSuggestionRepository aiAudienceSuggestionRepository;

    public AudienceArchitectService(ClaudeApiClient claudeClient,
                                     AiProperties aiProps,
                                     CampaignRepository campaignRepo,
                                     AdsetRepository adsetRepo,
                                     InsightDailyRepository insightRepo,
                                     AiContextBuilder aiContextBuilder,
                                     AiAudienceSuggestionRepository aiAudienceSuggestionRepository) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.campaignRepo = campaignRepo;
        this.adsetRepo = adsetRepo;
        this.insightRepo = insightRepo;
        this.aiContextBuilder = aiContextBuilder;
        this.aiAudienceSuggestionRepository = aiAudienceSuggestionRepository;
    }

    /**
     * Generate audience targeting suggestions for a client.
     */
    @Transactional
    public AiAudienceSuggestion suggestAudiences(UUID agencyId, UUID clientId) {
        String sharedContext = aiContextBuilder.buildContext(agencyId, clientId);

        // Module-specific targeting details
        List<Campaign> campaigns = campaignRepo.findAllByAgencyIdAndClientId(agencyId, clientId);
        List<Campaign> activeCampaigns = campaigns.stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()) || "PUBLISHED".equals(c.getStatus()))
                .toList();

        List<Map<String, String>> currentTargeting = new ArrayList<>();
        for (Campaign c : activeCampaigns) {
            List<Adset> adsets = adsetRepo.findAllByCampaignId(c.getId());
            for (Adset a : adsets) {
                if ("ACTIVE".equals(a.getStatus()) || "PUBLISHED".equals(a.getStatus())) {
                    Map<String, String> t = new LinkedHashMap<>();
                    t.put("campaignName", c.getName());
                    t.put("adsetName", a.getName());
                    t.put("targeting", a.getTargetingJson());
                    t.put("dailyBudget", a.getDailyBudget().toPlainString());
                    currentTargeting.add(t);
                }
            }
        }

        // Best performing entities for audience inspiration
        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(29);
        List<InsightDaily> insights = insightRepo
                .findAllByAgencyIdAndClientIdAndDateBetween(agencyId, clientId, from, to);

        // Aggregate by entity to find best performers
        Map<String, PerformanceAgg> byEntity = new LinkedHashMap<>();
        for (InsightDaily i : insights) {
            String key = i.getEntityType() + ":" + i.getEntityId();
            byEntity.computeIfAbsent(key, k -> new PerformanceAgg(i.getEntityType(), i.getEntityId()))
                    .add(i);
        }
        List<Map<String, Object>> topPerformers = byEntity.values().stream()
                .sorted((a, b) -> b.roas.compareTo(a.roas))
                .limit(5)
                .map(agg -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("entityType", agg.entityType);
                    m.put("entityId", agg.entityId);
                    m.put("spend", agg.spend.setScale(2, RoundingMode.HALF_UP));
                    m.put("conversions", agg.conversions.setScale(2, RoundingMode.HALF_UP));
                    m.put("roas", agg.spend.compareTo(BigDecimal.ZERO) > 0
                            ? agg.conversionValue.divide(agg.spend, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
                    return m;
                })
                .toList();

        StringBuilder ctx = new StringBuilder();
        ctx.append(sharedContext);

        ctx.append("\nCurrent Targeting Details (module-specific):\n");
        for (Map<String, String> t : currentTargeting) {
            ctx.append("- Campaign: ").append(t.get("campaignName"))
               .append(" > ").append(t.get("adsetName"))
               .append(" | Budget: $").append(t.get("dailyBudget"))
               .append(" | Targeting: ").append(t.get("targeting"))
               .append("\n");
        }

        ctx.append("\nTop Performing Entities for Targeting Inspiration:\n");
        for (Map<String, Object> tp : topPerformers) {
            ctx.append("- ").append(tp.get("entityType")).append(": ").append(tp.get("entityId"))
               .append(" | Spend: $").append(tp.get("spend"))
               .append(" | Conversions: ").append(tp.get("conversions"))
               .append(" | ROAS: ").append(tp.get("roas"))
               .append("\n");
        }

        String systemPrompt = """
                You are a Meta Ads targeting expert and audience strategist.
            Based on the shared client context and targeting data, suggest 3-5 audience segments.
                For each audience, provide detailed Meta Ads targeting specifications.
                
                Respond with STRICT JSON only, no markdown:
                {
                  "recommended_audiences": [
                    {
                      "name": "string — descriptive audience name",
                      "description": "string — who this audience is",
                      "targeting": {
                        "age_min": 18,
                        "age_max": 65,
                        "genders": [0],
                        "geo_locations": { "countries": ["US"] },
                        "interests": [{"id": "6003...", "name": "Interest name"}],
                        "custom_audiences": [],
                        "excluded_custom_audiences": []
                      },
                      "estimated_size": "string — e.g. '500K - 1M'",
                      "rationale": "string — why this audience fits the business",
                      "confidence": "HIGH | MEDIUM | LOW",
                      "suggested_daily_budget": "string — e.g. '$20-$50'",
                      "funnel_stage": "TOFU | MOFU | BOFU"
                    }
                  ],
                  "exclusion_recommendations": [
                    {
                      "description": "string — what to exclude and why",
                      "targeting_spec": "string — the exclusion targeting"
                    }
                  ],
                  "overlap_warnings": [
                    "string — warning about audience overlap between segments"
                  ],
                  "strategy_notes": "string — overall targeting strategy advice"
                }
                """;

        ClaudeResponse response = claudeClient.sendMessage(
                systemPrompt, ctx.toString(),
                MODULE, agencyId, clientId);

        if (!response.isSuccess()) {
            log.warn("Audience Architect: Claude call failed for client {}: {}", clientId, response.error());
            throw new IllegalStateException("AI analysis failed: " + response.error());
        }

        JsonNode json = claudeClient.parseJson(response.text());
        if (json == null) {
            log.warn("Audience Architect: Failed to parse Claude response for client {}", clientId);
            throw new IllegalStateException("Failed to parse AI response");
        }

        AiAudienceSuggestion saved = new AiAudienceSuggestion();
        saved.setAgencyId(agencyId);
        saved.setClientId(clientId);
        saved.setSuggestionJson(json.toString());
        saved.setCreatedAt(OffsetDateTime.now());
        saved = aiAudienceSuggestionRepository.save(saved);

        log.info("Audience Architect: generated {} audience suggestions for client {}",
                json.has("recommended_audiences") ? json.get("recommended_audiences").size() : 0,
                clientId);
        return saved;
    }

    // ──────── Helpers ────────

    private String fmt(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
    }

    private static class PerformanceAgg {
        final String entityType;
        final UUID entityId;
        BigDecimal spend = BigDecimal.ZERO;
        BigDecimal conversions = BigDecimal.ZERO;
        BigDecimal conversionValue = BigDecimal.ZERO;
        BigDecimal roas = BigDecimal.ZERO;

        PerformanceAgg(String entityType, UUID entityId) {
            this.entityType = entityType;
            this.entityId = entityId;
        }

        void add(InsightDaily i) {
            spend = spend.add(i.getSpend() != null ? i.getSpend() : BigDecimal.ZERO);
            conversions = conversions.add(i.getConversions() != null ? i.getConversions() : BigDecimal.ZERO);
            conversionValue = conversionValue.add(
                    i.getConversionValue() != null ? i.getConversionValue() : BigDecimal.ZERO);
            if (spend.compareTo(BigDecimal.ZERO) > 0) {
                roas = conversionValue.divide(spend, 4, RoundingMode.HALF_UP);
            }
        }
    }
}
