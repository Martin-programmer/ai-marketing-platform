package com.amp.ai;

import com.amp.ai.ClaudeApiClient.ClaudeResponse;
import com.amp.campaigns.Campaign;
import com.amp.campaigns.CampaignRepository;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.insights.InsightDailyRepository;
import com.amp.insights.KpiSummary;
import com.amp.reports.NarrativeSections;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Uses Claude (Sonnet) to generate narrative sections for performance reports.
 */
@Service
public class AiReporterService {

    private static final Logger log = LoggerFactory.getLogger(AiReporterService.class);

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;
    private final InsightDailyRepository insightRepo;
    private final CampaignRepository campaignRepo;
    private final AiSuggestionRepository suggestionRepo;
        private final AiActionLogRepository actionLogRepo;
    private final ClientRepository clientRepo;
        private final AiContextBuilder aiContextBuilder;
        private final AiCrossModuleSupportService aiCrossModuleSupportService;
        private final ObjectMapper objectMapper;

    public AiReporterService(ClaudeApiClient claudeClient,
                             AiProperties aiProps,
                             InsightDailyRepository insightRepo,
                             CampaignRepository campaignRepo,
                             AiSuggestionRepository suggestionRepo,
                             AiActionLogRepository actionLogRepo,
                             ClientRepository clientRepo,
                             AiContextBuilder aiContextBuilder,
                             AiCrossModuleSupportService aiCrossModuleSupportService,
                             ObjectMapper objectMapper) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.insightRepo = insightRepo;
        this.campaignRepo = campaignRepo;
        this.suggestionRepo = suggestionRepo;
        this.actionLogRepo = actionLogRepo;
        this.clientRepo = clientRepo;
        this.aiContextBuilder = aiContextBuilder;
        this.aiCrossModuleSupportService = aiCrossModuleSupportService;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates AI narrative sections for a report period.
     * Returns null if Claude call fails (caller should fall back to basic template).
     */
    public NarrativeSections generateNarrative(UUID agencyId, UUID clientId,
                                                LocalDate periodStart, LocalDate periodEnd) {
        try {
            // 1. Client info
            String clientName = clientRepo.findByIdAndAgencyId(clientId, agencyId)
                    .map(Client::getName).orElse("Client");

            // 2. KPI current period
            KpiSummary current = insightRepo.aggregateKpis(agencyId, clientId, periodStart, periodEnd);

            // 3. KPI previous period for comparison
            long days = ChronoUnit.DAYS.between(periodStart, periodEnd);
            KpiSummary previous = insightRepo.aggregateKpis(
                    agencyId, clientId,
                    periodStart.minusDays(days), periodStart.minusDays(1));

            // 4. Campaign activity during the period
            List<Campaign> campaigns = campaignRepo.findAllByAgencyIdAndClientId(agencyId, clientId);
            OffsetDateTime periodStartOdt = periodStart.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime periodEndOdt = periodEnd.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

            List<Campaign> newCampaigns = campaigns.stream()
                    .filter(c -> c.getCreatedAt() != null
                            && !c.getCreatedAt().isBefore(periodStartOdt)
                            && c.getCreatedAt().isBefore(periodEndOdt))
                    .toList();

            List<Campaign> pausedCampaigns = campaigns.stream()
                    .filter(c -> "PAUSED".equals(c.getStatus()))
                    .toList();

            List<Campaign> activeCampaigns = campaigns.stream()
                    .filter(c -> "ACTIVE".equals(c.getStatus()) || "PUBLISHED".equals(c.getStatus()))
                    .toList();

            // 5. AI Suggestions activity during the period
            List<AiSuggestion> allSuggestions = suggestionRepo
                    .findAllByAgencyIdAndClientId(agencyId, clientId);
            List<AiSuggestion> periodSuggestions = allSuggestions.stream()
                    .filter(s -> s.getCreatedAt() != null
                            && !s.getCreatedAt().isBefore(periodStartOdt)
                            && s.getCreatedAt().isBefore(periodEndOdt))
                    .toList();

            long suggestionsCreated = periodSuggestions.size();
            long suggestionsApproved = periodSuggestions.stream()
                    .filter(s -> "APPROVED".equals(s.getStatus()) || "APPLIED".equals(s.getStatus()))
                    .count();
            long suggestionsApplied = periodSuggestions.stream()
                    .filter(s -> "APPLIED".equals(s.getStatus()))
                    .count();

            List<AiActionLog> periodActions = actionLogRepo.findAllByAgencyIdAndClientId(agencyId, clientId)
                    .stream()
                    .filter(a -> a.getCreatedAt() != null
                            && !a.getCreatedAt().isBefore(periodStartOdt)
                            && a.getCreatedAt().isBefore(periodEndOdt))
                    .toList();

            CompletableFuture<String> sharedContextFuture = CompletableFuture.supplyAsync(
                    () -> aiContextBuilder.buildContext(agencyId, clientId));
            CompletableFuture<String> aiActivityFuture = aiCrossModuleSupportService.buildAiActivitySummaryAsync(
                    agencyId, clientId, periodStart, periodEnd, true, "month");

            // 6. Build context payload
            StringBuilder ctx = new StringBuilder();
            ctx.append("CLIENT: ").append(clientName).append("\n");
            ctx.append("PERIOD: ").append(periodStart).append(" to ").append(periodEnd).append("\n\n");

            ctx.append("=== CURRENT PERIOD KPIs ===\n");
            appendKpis(ctx, current);

            ctx.append("=== PREVIOUS PERIOD KPIs ===\n");
            if (previous != null && previous.getTotalImpressions() != null && previous.getTotalImpressions() > 0) {
                appendKpis(ctx, previous);
            } else {
                ctx.append("No data for previous period.\n\n");
            }

            ctx.append("=== CAMPAIGNS ===\n");
            ctx.append("Active campaigns: ").append(activeCampaigns.size()).append("\n");
            ctx.append("New campaigns this period: ").append(newCampaigns.size()).append("\n");
            for (Campaign c : newCampaigns) {
                ctx.append("  - ").append(c.getName()).append(" (").append(c.getObjective()).append(")\n");
            }
            ctx.append("Paused campaigns: ").append(pausedCampaigns.size()).append("\n");
            for (Campaign c : pausedCampaigns) {
                ctx.append("  - ").append(c.getName()).append("\n");
            }

            ctx.append("\n=== AI OPTIMIZATION ACTIVITY ===\n");
            ctx.append("Suggestions created: ").append(suggestionsCreated).append("\n");
            ctx.append("Suggestions approved: ").append(suggestionsApproved).append("\n");
            ctx.append("Suggestions applied: ").append(suggestionsApplied).append("\n");
            Map<String, Long> byType = periodSuggestions.stream()
                    .collect(Collectors.groupingBy(AiSuggestion::getSuggestionType, Collectors.counting()));
            if (!byType.isEmpty()) {
                ctx.append("By type: ").append(byType).append("\n");
            }
                        if (!periodActions.isEmpty()) {
                                ctx.append("Applied action logs this period: ").append(periodActions.size()).append("\n");
                                periodActions.stream().limit(5).forEach(action -> ctx.append("- ")
                                                .append(summarizeActionForPrompt(action, allSuggestions))
                                                .append("\n"));
                        }
                        String aiActivitySummary = aiActivityFuture.join();
                        if (aiActivitySummary != null && !aiActivitySummary.isBlank()) {
                                ctx.append("\n=== AI ACTIVITY SUMMARY ===\n").append(aiActivitySummary).append("\n");
                        }
                        ctx.append("\n=== SHARED CLIENT CONTEXT ===\n")
                                        .append(sharedContextFuture.join())
                                        .append("\n");

            // 7. Call Claude (Sonnet — fast and cheap)
            String systemPrompt = """
                    You are a marketing consultant writing a monthly performance report.
                    Based on the provided data, write the following sections:
                    1) Executive Summary (3-5 sentences overview of overall performance)
                    2) Campaign Highlights (notable campaigns, launches, wins)
                    3) Areas for Improvement (metrics that declined or need attention)
                    4) Actions Taken (AI optimizations applied, campaign changes made)
                    5) Recommendations for Next Month (actionable next steps)
                    
                    Be data-driven and reference specific numbers from the data.
                    Keep each section concise but insightful. Use the client's language.
                    
                    Respond with STRICT JSON only, no markdown:
                    {
                      "executiveSummary": "...",
                      "campaignHighlights": "...",
                      "areasForImprovement": "...",
                      "actionsTaken": "...",
                      "recommendations": "..."
                    }
                    """;

            ClaudeResponse response = claudeClient.sendMessage(
                    systemPrompt, ctx.toString(),
                    "AI_REPORTER", agencyId, clientId);

            if (!response.isSuccess()) {
                log.warn("AI Reporter: Claude call failed for client {}: {}", clientId, response.error());
                return null;
            }

            // 8. Parse response
            JsonNode json = claudeClient.parseJson(response.text());
            if (json == null) {
                log.warn("AI Reporter: Failed to parse Claude response as JSON for client {}", clientId);
                return null;
            }

            return new NarrativeSections(
                    textOrNull(json, "executiveSummary"),
                    textOrNull(json, "campaignHighlights"),
                    textOrNull(json, "areasForImprovement"),
                    textOrNull(json, "actionsTaken"),
                    textOrNull(json, "recommendations")
            );

        } catch (Exception e) {
            log.error("AI Reporter: Failed to generate narrative for client {}: {}",
                    clientId, e.getMessage(), e);
            return null;
        }
    }

    // ──────── Helpers ────────

    private void appendKpis(StringBuilder sb, KpiSummary k) {
        sb.append("Spend: $").append(fmt(k.getTotalSpend())).append("\n");
        sb.append("Impressions: ").append(k.getTotalImpressions()).append("\n");
        sb.append("Clicks: ").append(k.getTotalClicks()).append("\n");
        sb.append("Conversions: ").append(fmt(k.getTotalConversions())).append("\n");
        sb.append("CTR: ").append(String.format("%.2f",
                k.getAvgCtr() != null ? k.getAvgCtr() : 0.0)).append("%\n");
        sb.append("CPC: $").append(fmt(k.getAvgCpc())).append("\n");
        sb.append("ROAS: ").append(fmt(k.getAvgRoas())).append("\n\n");
    }

    private String fmt(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
    }

    private static String textOrNull(JsonNode json, String field) {
        return json.has(field) && !json.get(field).isNull()
                ? json.get(field).asText()
                : null;
    }

        private String summarizeActionForPrompt(AiActionLog action, List<AiSuggestion> suggestions) {
                AiSuggestion suggestion = suggestions.stream()
                                .filter(s -> s.getId().equals(action.getSuggestionId()))
                                .findFirst()
                                .orElse(null);
                String type = suggestion != null ? suggestion.getSuggestionType() : "AI_ACTION";
                String rationale = suggestion != null ? suggestion.getRationale() : "Applied AI action";
                return type + ": " + truncate(rationale, 140) + " | Before/after: "
                                + truncate(summarizeSnapshot(action.getResultSnapshotJson()), 160);
        }

        private String summarizeSnapshot(String snapshotJson) {
                if (snapshotJson == null || snapshotJson.isBlank()) {
                        return "not available";
                }
                try {
                        JsonNode node = objectMapper.readTree(snapshotJson);
                        JsonNode before = node.get("before");
                        JsonNode after = node.get("after");
                        if (before == null || after == null) {
                                return snapshotJson;
                        }
                        List<String> changes = new java.util.ArrayList<>();
                        appendSnapshotChange(changes, before, after, "status");
                        appendSnapshotChange(changes, before, after, "daily_budget");
                        appendSnapshotChange(changes, before, after, "lifetime_budget");
                        return changes.isEmpty() ? "captured" : String.join(", ", changes);
                } catch (Exception e) {
                        return snapshotJson;
                }
        }

        private void appendSnapshotChange(List<String> changes, JsonNode before, JsonNode after, String field) {
                if (before.has(field) && after.has(field)) {
                        String beforeVal = before.get(field).asText();
                        String afterVal = after.get(field).asText();
                        if (!beforeVal.equals(afterVal)) {
                                changes.add(field + " " + beforeVal + " → " + afterVal);
                        }
                }
        }

        private String truncate(String value, int max) {
                if (value == null) {
                        return "";
                }
                String cleaned = value.replaceAll("\\s+", " ").trim();
                return cleaned.length() <= max ? cleaned : cleaned.substring(0, max - 1) + "…";
        }
}
