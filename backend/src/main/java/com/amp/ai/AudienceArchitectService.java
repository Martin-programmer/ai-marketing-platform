package com.amp.ai;

import com.amp.ai.ClaudeApiClient.ClaudeResponse;
import com.amp.campaigns.Adset;
import com.amp.campaigns.AdsetRepository;
import com.amp.campaigns.Campaign;
import com.amp.campaigns.CampaignRepository;
import com.amp.clients.Client;
import com.amp.clients.ClientProfile;
import com.amp.clients.ClientProfileRepository;
import com.amp.clients.ClientRepository;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.amp.insights.KpiSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    private final ClientRepository clientRepo;
    private final ClientProfileRepository profileRepo;
    private final CampaignRepository campaignRepo;
    private final AdsetRepository adsetRepo;
    private final InsightDailyRepository insightRepo;
    private final ObjectMapper objectMapper;

    public AudienceArchitectService(ClaudeApiClient claudeClient,
                                     AiProperties aiProps,
                                     ClientRepository clientRepo,
                                     ClientProfileRepository profileRepo,
                                     CampaignRepository campaignRepo,
                                     AdsetRepository adsetRepo,
                                     InsightDailyRepository insightRepo,
                                     ObjectMapper objectMapper) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.clientRepo = clientRepo;
        this.profileRepo = profileRepo;
        this.campaignRepo = campaignRepo;
        this.adsetRepo = adsetRepo;
        this.insightRepo = insightRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate audience targeting suggestions for a client.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> suggestAudiences(UUID agencyId, UUID clientId) {
        Client client = clientRepo.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        // 1. Client profile
        String profileJson = "{}";
        String website = null;
        ClientProfile profile = profileRepo.findByClientId(clientId).orElse(null);
        if (profile != null) {
            profileJson = profile.getProfileJson();
            website = profile.getWebsite();
        }

        // 2. Active campaigns and their targeting
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

        // 3. Performance data — last 30 days top performers
        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(29);
        KpiSummary kpis = insightRepo.aggregateKpis(agencyId, clientId, from, to);

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

        // 4. Build context
        StringBuilder ctx = new StringBuilder();
        ctx.append("CLIENT: ").append(client.getName()).append("\n");
        ctx.append("INDUSTRY: ").append(client.getIndustry() != null ? client.getIndustry() : "Unknown").append("\n");
        if (website != null) {
            ctx.append("WEBSITE: ").append(website).append("\n");
        }
        ctx.append("\n=== CLIENT PROFILE ===\n").append(profileJson).append("\n");

        ctx.append("\n=== CURRENT TARGETING (").append(currentTargeting.size()).append(" active ad sets) ===\n");
        for (Map<String, String> t : currentTargeting) {
            ctx.append("Campaign: ").append(t.get("campaignName"))
               .append(" > ").append(t.get("adsetName"))
               .append(" | Budget: $").append(t.get("dailyBudget"))
               .append(" | Targeting: ").append(t.get("targeting"))
               .append("\n");
        }

        ctx.append("\n=== PERFORMANCE SUMMARY (last 30 days) ===\n");
        if (kpis != null && kpis.getTotalImpressions() != null) {
            ctx.append("Spend: $").append(fmt(kpis.getTotalSpend())).append("\n");
            ctx.append("Impressions: ").append(kpis.getTotalImpressions()).append("\n");
            ctx.append("Clicks: ").append(kpis.getTotalClicks()).append("\n");
            ctx.append("Conversions: ").append(fmt(kpis.getTotalConversions())).append("\n");
            ctx.append("ROAS: ").append(fmt(kpis.getAvgRoas())).append("\n");
        } else {
            ctx.append("No performance data available.\n");
        }

        ctx.append("\n=== TOP 5 PERFORMING ENTITIES ===\n");
        for (Map<String, Object> tp : topPerformers) {
            ctx.append(tp.get("entityType")).append(": ").append(tp.get("entityId"))
               .append(" | Spend: $").append(tp.get("spend"))
               .append(" | Conversions: ").append(tp.get("conversions"))
               .append(" | ROAS: ").append(tp.get("roas"))
               .append("\n");
        }

        // 5. Call Claude
        String systemPrompt = """
                You are a Meta Ads targeting expert and audience strategist.
                Based on the business profile and performance data, suggest 3-5 audience segments.
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
            return Map.of("error", "AI analysis failed: " + response.error());
        }

        // 6. Parse response
        JsonNode json = claudeClient.parseJson(response.text());
        if (json == null) {
            log.warn("Audience Architect: Failed to parse Claude response for client {}", clientId);
            return Map.of("error", "Failed to parse AI response");
        }

        try {
            Map<String, Object> result = objectMapper.treeToValue(json, Map.class);
            log.info("Audience Architect: generated {} audience suggestions for client {}",
                    json.has("recommended_audiences") ? json.get("recommended_audiences").size() : 0,
                    clientId);
            return result;
        } catch (Exception e) {
            log.warn("Audience Architect: JSON conversion failed: {}", e.getMessage());
            return Map.of("error", "JSON conversion failed",
                          "raw", response.text());
        }
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
