package com.amp.ai;

import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.campaigns.AdRepository;
import com.amp.campaigns.AdsetRepository;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.amp.meta.MetaConnection;
import com.amp.meta.MetaConnectionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Daily performance optimizer that analyses insight data, applies 10 rules,
 * filters through guardrails, enriches with Claude LLM, and persists
 * {@link AiSuggestion} records.
 */
@Service
public class PerformanceOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceOptimizerService.class);
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final AiProperties aiProps;
    private final ClaudeApiClient claudeClient;
    private final GuardrailsEngine guardrails;
    private final AiSuggestionRepository suggestionRepo;
    private final InsightDailyRepository insightRepo;
    private final AdsetRepository adsetRepo;
    private final AdRepository adRepo;
    private final MetaConnectionRepository metaConnRepo;
    private final ClientRepository clientRepo;
    private final AiContextBuilder aiContextBuilder;
    private final AiCrossModuleSupportService aiCrossModuleSupportService;
    private final ObjectMapper objectMapper;

    public PerformanceOptimizerService(AiProperties aiProps,
                                       ClaudeApiClient claudeClient,
                                       GuardrailsEngine guardrails,
                                       AiSuggestionRepository suggestionRepo,
                                       InsightDailyRepository insightRepo,
                                       AdsetRepository adsetRepo,
                                       AdRepository adRepo,
                                       MetaConnectionRepository metaConnRepo,
                                       ClientRepository clientRepo,
                                       AiContextBuilder aiContextBuilder,
                                       AiCrossModuleSupportService aiCrossModuleSupportService,
                                       ObjectMapper objectMapper) {
        this.aiProps = aiProps;
        this.claudeClient = claudeClient;
        this.guardrails = guardrails;
        this.suggestionRepo = suggestionRepo;
        this.insightRepo = insightRepo;
        this.adsetRepo = adsetRepo;
        this.adRepo = adRepo;
        this.metaConnRepo = metaConnRepo;
        this.clientRepo = clientRepo;
        this.aiContextBuilder = aiContextBuilder;
        this.aiCrossModuleSupportService = aiCrossModuleSupportService;
        this.objectMapper = objectMapper;
    }

    // ══════════════════════════════════════════
    // SCHEDULING & ENTRY POINTS
    // ══════════════════════════════════════════

    @PostConstruct
    public void onInit() {
        log.info("PerformanceOptimizerService initialized, scheduled optimization enabled");
        log.info("PerformanceOptimizerService cron: {}", aiProps.getOptimizer().getScheduleCron());
    }

    /**
     * Scheduled daily optimisation run for all connected clients.
     */
    @Scheduled(cron = "${ai.optimizer.schedule-cron:0 0 5 * * *}")
    public void scheduledOptimizationRun() {
        if (!aiProps.getOptimizer().isEnabled()) {
            log.info("Performance Optimizer is disabled, skipping scheduled run");
            return;
        }
        log.info("Starting scheduled optimization run");
        runForAllClients();
    }

    /**
     * Run optimisation for all connected clients.
     */
    public Map<String, Object> runForAllClients() {
        List<MetaConnection> connections = metaConnRepo.findByStatus("CONNECTED");
        int totalFindings = 0;
        int totalSuggestions = 0;
        List<String> errors = new ArrayList<>();

        for (MetaConnection conn : connections) {
            try {
                TenantContextHolder.set(systemTenantContext(conn.getAgencyId(), conn.getClientId()));
                var result = runForClient(conn.getAgencyId(), conn.getClientId());
                totalFindings += (int) result.getOrDefault("findingsCount", 0);
                totalSuggestions += (int) result.getOrDefault("suggestionsCreated", 0);
            } catch (Exception e) {
                log.error("Optimization failed for client {}: {}", conn.getClientId(), e.getMessage());
                errors.add(conn.getClientId() + ": " + e.getMessage());
            } finally {
                TenantContextHolder.clear();
            }
        }

        log.info("Optimization run complete: {} clients, {} findings, {} suggestions created",
                connections.size(), totalFindings, totalSuggestions);

        return Map.of(
                "clientsProcessed", connections.size(),
                "totalFindings", totalFindings,
                "totalSuggestions", totalSuggestions,
                "errors", errors);
    }

    /**
     * Run optimisation for a specific client.
     */
    @Transactional
    public Map<String, Object> runForClient(UUID agencyId, UUID clientId) {
        log.info("Running optimization for client {}", clientId);

        LocalDate today = LocalDate.now();
        LocalDate from30 = today.minusDays(30);
        LocalDate from14 = today.minusDays(14);
        LocalDate from7 = today.minusDays(7);

        List<InsightDaily> insights30 = insightRepo.findAllByAgencyIdAndClientIdAndDateBetween(agencyId, clientId, from30, today);
        List<InsightDaily> insights14 = insightRepo.findAllByAgencyIdAndClientIdAndDateBetween(agencyId, clientId, from14, today);
        List<InsightDaily> insights7 = insightRepo.findAllByAgencyIdAndClientIdAndDateBetween(agencyId, clientId, from7, today);

        if (insights14.isEmpty()) {
            log.info("No insights data for client {}, skipping", clientId);
            return Map.of("findingsCount", 0, "suggestionsCreated", 0, "message", "Not enough data to analyze");
        }

        AnalysisResult analysis = analyzeInsights(agencyId, clientId, insights30, insights14, insights7);

        return Map.of(
                "findingsCount", analysis.findingsCount(),
                "suggestionsCreated", analysis.createdSuggestions().size());
    }

    /**
     * Run optimisation for a single campaign by limiting the scope to its ads/adsets.
     */
    @Transactional
    public Map<String, Object> runForCampaign(UUID agencyId, UUID clientId, UUID campaignId) {
        log.info("Running campaign-scoped optimization for campaign {}", campaignId);

        LocalDate today = LocalDate.now();
        LocalDate from30 = today.minusDays(30);
        LocalDate from14 = today.minusDays(14);
        LocalDate from7 = today.minusDays(7);

        List<UUID> adsetIds = adsetRepo.findIdsByCampaignId(campaignId);
        List<UUID> adIds = adsetIds.isEmpty() ? List.of() : adRepo.findIdsByAdsetIdIn(adsetIds);

        List<InsightDaily> insights30 = loadCampaignScopedInsights(agencyId, clientId, adsetIds, adIds, from30, today);
        List<InsightDaily> insights14 = loadCampaignScopedInsights(agencyId, clientId, adsetIds, adIds, from14, today);
        List<InsightDaily> insights7 = loadCampaignScopedInsights(agencyId, clientId, adsetIds, adIds, from7, today);

        if (insights14.isEmpty()) {
            log.info("No scoped insights data for campaign {}, skipping", campaignId);
            return Map.of(
                    "findingsCount", 0,
                    "suggestionsCreated", 0,
                    "message", "Not enough data to analyze",
                    "suggestions", List.of());
        }

        AnalysisResult analysis = analyzeInsights(agencyId, clientId, insights30, insights14, insights7);

        return Map.of(
                "findingsCount", analysis.findingsCount(),
                "suggestionsCreated", analysis.createdSuggestions().size(),
                "message", analysis.createdSuggestions().isEmpty() ? "No issues found for this campaign" : "Analysis complete",
                "suggestions", analysis.createdSuggestions().stream().map(SuggestionResponse::from).toList());
    }

    private List<InsightDaily> loadCampaignScopedInsights(UUID agencyId, UUID clientId,
                                                          List<UUID> adsetIds, List<UUID> adIds,
                                                          LocalDate from, LocalDate to) {
        List<InsightDaily> insights = new ArrayList<>();
        if (!adsetIds.isEmpty()) {
            insights.addAll(insightRepo.findAllByAgencyIdAndClientIdAndEntityTypeAndEntityIdInAndDateBetween(
                    agencyId, clientId, "ADSET", adsetIds, from, to));
        }
        if (!adIds.isEmpty()) {
            insights.addAll(insightRepo.findAllByAgencyIdAndClientIdAndEntityTypeAndEntityIdInAndDateBetween(
                    agencyId, clientId, "AD", adIds, from, to));
        }
        return insights;
    }

    private AnalysisResult analyzeInsights(UUID agencyId, UUID clientId,
                                           List<InsightDaily> insights30,
                                           List<InsightDaily> insights14,
                                           List<InsightDaily> insights7) {

        // Group insights by entity
        Map<String, List<InsightDaily>> byEntity14 = insights14.stream()
                .collect(Collectors.groupingBy(i -> i.getEntityType() + ":" + i.getEntityId()));
        Map<String, List<InsightDaily>> byEntity7 = insights7.stream()
                .collect(Collectors.groupingBy(i -> i.getEntityType() + ":" + i.getEntityId()));
        Map<String, List<InsightDaily>> byEntity30 = insights30.stream()
                .collect(Collectors.groupingBy(i -> i.getEntityType() + ":" + i.getEntityId()));

        // Run all rules
        List<RuleFinding> findings = new ArrayList<>();

        for (Map.Entry<String, List<InsightDaily>> entry : byEntity14.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String entityType = parts[0];
            UUID entityId = UUID.fromString(parts[1]);
            List<InsightDaily> data14 = entry.getValue();
            List<InsightDaily> data7 = byEntity7.getOrDefault(entry.getKey(), List.of());
            List<InsightDaily> data30 = byEntity30.getOrDefault(entry.getKey(), List.of());

            // Rule 1: Frequency Fatigue
            checkFrequencyFatigue(findings, entityType, entityId, data7, data14);
            // Rule 2: Zero Conversion Spender
            checkZeroConversionSpender(findings, entityType, entityId, data7);
            // Rule 3: CPA Spike
            checkCpaSpike(findings, entityType, entityId, data7, data14);
            // Rule 4: Strong Performer — Scale Up
            checkStrongPerformer(findings, entityType, entityId, data7);
            // Rule 5: Weak Performer — Scale Down
            checkWeakPerformer(findings, entityType, entityId, data7);
            // Rule 6: CTR Erosion
            checkCtrErosion(findings, entityType, entityId, data7, data14);
            // Rule 7: Delivery Issues
            checkDeliveryIssues(findings, entityType, entityId, data7);
            // Rule 8: Creative Test Needed (future — reserved)
            // Rule 9: Copy Refresh
            checkCopyRefresh(findings, entityType, entityId, data30);
        }

        // Rule 10: Budget Reallocation (cross-entity)
        checkBudgetReallocation(findings, byEntity7);

        log.info("Found {} raw findings for client {}", findings.size(), clientId);

        // Filter through guardrails
        List<RuleFinding> passed = new ArrayList<>();
        for (RuleFinding f : findings) {
            String rejection = guardrails.checkGuardrails(f, agencyId, clientId);
            if (rejection == null) {
                passed.add(f);
            } else {
                log.debug("Finding blocked by guardrails: {} — {}", f.suggestionType(), rejection);
            }
        }

        log.info("{} findings passed guardrails for client {}", passed.size(), clientId);

        // Enrich with LLM and save as suggestions
        List<AiSuggestion> createdSuggestions = new ArrayList<>();
        for (RuleFinding f : passed) {
            try {
            CompletableFuture<AiCrossModuleSupportService.CreativeSuggestionEnrichment> creativeEnrichmentFuture =
                shouldAttachCreativeRecommendations(f)
                    ? aiCrossModuleSupportService.findCreativeSuggestionEnrichmentAsync(
                        agencyId, clientId, f.scopeType(), f.scopeId(), isCopyRefreshFinding(f))
                    : CompletableFuture.completedFuture(AiCrossModuleSupportService.CreativeSuggestionEnrichment.empty());
                String enrichedRationale = enrichWithLlm(f, agencyId, clientId);
                AiSuggestion saved = saveSuggestion(f, enrichedRationale, creativeEnrichmentFuture, agencyId, clientId);
                createdSuggestions.add(saved);
            } catch (Exception e) {
                log.warn("Failed to create suggestion for finding {}: {}", f.suggestionType(), e.getMessage());
            }
        }

        return new AnalysisResult(findings.size(), createdSuggestions);
    }

    private TenantContext systemTenantContext(UUID agencyId, UUID clientId) {
        return new TenantContext(agencyId, SYSTEM_USER_ID, "system@scheduled-job.local", "SYSTEM", clientId);
    }

    // ══════════════════════════════════════════
    // RULES
    // ══════════════════════════════════════════

    /** Rule 1: Frequency Fatigue — high frequency + CTR drop signals audience fatigue. */
    private void checkFrequencyFatigue(List<RuleFinding> findings, String entityType, UUID entityId,
                                        List<InsightDaily> data7, List<InsightDaily> data14) {
        if (data7.size() < 5) return;

        double avgFreq7 = data7.stream()
                .mapToDouble(i -> i.getFrequency() != null ? i.getFrequency().doubleValue() : 0)
                .average().orElse(0);
        if (avgFreq7 < aiProps.getOptimizer().getFrequencyThreshold()) return;

        double avgCtr7 = avgCtr(data7);
        double avgCtr14 = avgCtr(data14);
        if (avgCtr14 <= 0) return;

        double ctrDropPct = (avgCtr14 - avgCtr7) / avgCtr14;
        if (ctrDropPct > aiProps.getOptimizer().getCtrDropThreshold()) {
            String payload = String.format(
                    "{\"alert\":\"frequency_fatigue\",\"frequency\":%.2f,\"ctr_drop_pct\":%.1f,\"threshold\":%.1f}",
                    avgFreq7, ctrDropPct * 100, aiProps.getOptimizer().getFrequencyThreshold());

            findings.add(new RuleFinding("DIAGNOSTIC", "LOW", entityType, entityId,
                    String.format("Frequency %.1f (>%.1f) with CTR drop %.1f%%",
                            avgFreq7, aiProps.getOptimizer().getFrequencyThreshold(), ctrDropPct * 100),
                    payload, 0.85, 0, data7.size(), 0, false));
        }
    }

    /** Rule 2: Zero-conversion spender — significant spend with zero results. */
    private void checkZeroConversionSpender(List<RuleFinding> findings, String entityType, UUID entityId,
                                             List<InsightDaily> data7) {
        if (data7.size() < 7) return;

        double totalSpend = sumSpend(data7);
        double avgDailySpend = totalSpend / data7.size();
        long totalConversions = sumConversions(data7);

        if (totalConversions == 0 && totalSpend > avgDailySpend * 3) {
            String payload = String.format(
                    "{\"action\":\"pause\",\"reason\":\"zero_conversions\",\"spend_7d\":%.2f,\"conversions_7d\":0}",
                    totalSpend);

            findings.add(new RuleFinding("PAUSE", "MEDIUM", entityType, entityId,
                    String.format("Spent %.2f in 7 days with 0 conversions", totalSpend),
                    payload, 0.90, 0, data7.size(), 0, false));
        }
    }

    /** Rule 3: CPA Spike — cost per acquisition increased significantly. */
    private void checkCpaSpike(List<RuleFinding> findings, String entityType, UUID entityId,
                                List<InsightDaily> data7, List<InsightDaily> data14) {
        double spend7 = sumSpend(data7);
        long conv7 = sumConversions(data7);
        double spend14 = sumSpend(data14);
        long conv14 = sumConversions(data14);

        if (conv7 == 0 || conv14 == 0) return;

        double cpa7 = spend7 / conv7;
        double cpa14 = spend14 / conv14;
        if (cpa14 <= 0) return;

        double increasePct = (cpa7 - cpa14) / cpa14;
        if (increasePct > aiProps.getOptimizer().getCpaSpikeThreshold()) {
            String payload = String.format(
                    "{\"alert\":\"cpa_spike\",\"cpa_7d\":%.2f,\"cpa_14d\":%.2f,\"increase_pct\":%.1f}",
                    cpa7, cpa14, increasePct * 100);

            findings.add(new RuleFinding("DIAGNOSTIC", "MEDIUM", entityType, entityId,
                    String.format("CPA spiked from %.2f to %.2f (+%.0f%%)", cpa14, cpa7, increasePct * 100),
                    payload, 0.80, 0, data7.size(), conv7, false));
        }
    }

    /** Rule 4: Strong Performer — ROAS &gt; 3.0, recommend budget scale-up. */
    private void checkStrongPerformer(List<RuleFinding> findings, String entityType, UUID entityId,
                                       List<InsightDaily> data7) {
        double spend = sumSpend(data7);
        long conv = sumConversions(data7);
        double convValue = sumConversionValue(data7);

        if (conv < aiProps.getOptimizer().getMinConversions() || spend == 0) return;

        double roas = convValue / spend;
        if (roas > 3.0) {
            double cpa = spend / conv;
            double avgDailySpend = spend / data7.size();
            int changePercent = aiProps.getOptimizer().getBudgetChangeMaxPercent();
            double proposedBudget = avgDailySpend * (1 + changePercent / 100.0);

            String payload = String.format(
                    "{\"current_daily_budget\":%.2f,\"proposed_daily_budget\":%.2f,\"change_percent\":%d,\"roas_7d\":%.2f,\"cpa_7d\":%.2f}",
                    avgDailySpend, proposedBudget, changePercent, roas, cpa);

            findings.add(new RuleFinding("BUDGET_ADJUST", "MEDIUM", entityType, entityId,
                    String.format("ROAS %.2f with stable CPA %.2f — scale up %d%%", roas, cpa, changePercent),
                    payload, 0.85, changePercent, data7.size(), conv, true));
        }
    }

    /** Rule 5: Weak Performer — ROAS &lt; 1.5, recommend budget scale-down. */
    private void checkWeakPerformer(List<RuleFinding> findings, String entityType, UUID entityId,
                                     List<InsightDaily> data7) {
        double spend = sumSpend(data7);
        long conv = sumConversions(data7);
        double convValue = sumConversionValue(data7);

        if (spend == 0 || conv == 0) return;

        double roas = convValue / spend;
        if (roas < 1.5) {
            double avgDailySpend = spend / data7.size();
            int changePercent = -aiProps.getOptimizer().getBudgetChangeMaxPercent();
            double proposedBudget = avgDailySpend * (1 + changePercent / 100.0);

            String payload = String.format(
                    "{\"current_daily_budget\":%.2f,\"proposed_daily_budget\":%.2f,\"change_percent\":%d,\"roas_7d\":%.2f}",
                    avgDailySpend, proposedBudget, changePercent, roas);

            findings.add(new RuleFinding("BUDGET_ADJUST", "MEDIUM", entityType, entityId,
                    String.format("ROAS %.2f — weak performance, scale down %d%%", roas, Math.abs(changePercent)),
                    payload, 0.75, changePercent, data7.size(), conv, true));
        }
    }

    /** Rule 6: CTR Erosion — CTR dropped &gt;20 % between 14-day and 7-day window. */
    private void checkCtrErosion(List<RuleFinding> findings, String entityType, UUID entityId,
                                  List<InsightDaily> data7, List<InsightDaily> data14) {
        double avgCtr7 = avgCtr(data7);
        double avgCtr14 = avgCtr(data14);
        if (avgCtr14 == 0) return;

        double dropPct = (avgCtr14 - avgCtr7) / avgCtr14;
        if (dropPct > 0.20) {
            String payload = String.format(
                    "{\"alert\":\"ctr_erosion\",\"ctr_7d\":%.4f,\"ctr_14d\":%.4f,\"drop_pct\":%.1f}",
                    avgCtr7, avgCtr14, dropPct * 100);

            findings.add(new RuleFinding("DIAGNOSTIC", "LOW", entityType, entityId,
                    String.format("CTR dropped %.1f%% (from %.3f to %.3f)", dropPct * 100, avgCtr14, avgCtr7),
                    payload, 0.80, 0, data7.size(), 0, false));
        }
    }

    /** Rule 7: Delivery Issues — very low impressions over 5+ days. */
    private void checkDeliveryIssues(List<RuleFinding> findings, String entityType, UUID entityId,
                                      List<InsightDaily> data7) {
        if (data7.size() < 5) return;

        long lowImpressionDays = data7.stream().filter(i -> i.getImpressions() < 500).count();
        if (lowImpressionDays >= 5) {
            double avgImpressions = data7.stream().mapToDouble(i -> (double) i.getImpressions()).average().orElse(0);
            String payload = String.format(
                    "{\"alert\":\"delivery_issues\",\"avg_daily_impressions\":%.0f,\"low_days\":%d}",
                    avgImpressions, lowImpressionDays);

            findings.add(new RuleFinding("DIAGNOSTIC", "LOW", entityType, entityId,
                    String.format("Only %.0f avg daily impressions for %d days — possible delivery issue",
                            avgImpressions, lowImpressionDays),
                    payload, 0.75, 0, data7.size(), 0, false));
        }
    }

    /** Rule 9: Copy Refresh — CTR trending down over 30 days. */
    private void checkCopyRefresh(List<RuleFinding> findings, String entityType, UUID entityId,
                                   List<InsightDaily> data30) {
        if (data30.size() < 25) return;

        int half = data30.size() / 2;
        double ctrFirst = data30.subList(0, half).stream()
                .mapToDouble(i -> i.getCtr() != null ? i.getCtr().doubleValue() : 0).average().orElse(0);
        double ctrSecond = data30.subList(half, data30.size()).stream()
                .mapToDouble(i -> i.getCtr() != null ? i.getCtr().doubleValue() : 0).average().orElse(0);

        if (ctrFirst > 0 && ctrSecond < ctrFirst * 0.85) {
            String payload = String.format(
                    "{\"suggestion\":\"copy_refresh\",\"ctr_first_half\":%.4f,\"ctr_second_half\":%.4f}",
                    ctrFirst, ctrSecond);

            findings.add(new RuleFinding("COPY_REFRESH", "LOW", entityType, entityId,
                    String.format("CTR trending down over 30 days (%.3f → %.3f) — copy refresh recommended",
                            ctrFirst, ctrSecond),
                    payload, 0.70, 0, data30.size(), 0, false));
        }
    }

    /** Rule 10: Budget Reallocation — shift spend from worst to best performer. */
    private void checkBudgetReallocation(List<RuleFinding> findings,
                                          Map<String, List<InsightDaily>> byEntity7) {
        Map<UUID, Double> entityRoas = new HashMap<>();
        Map<UUID, Double> entitySpend = new HashMap<>();

        for (Map.Entry<String, List<InsightDaily>> entry : byEntity7.entrySet()) {
            UUID entityId = UUID.fromString(entry.getKey().split(":")[1]);
            List<InsightDaily> data = entry.getValue();
            double spend = sumSpend(data);
            double convValue = sumConversionValue(data);

            if (spend > 0) {
                entityRoas.put(entityId, convValue / spend);
                entitySpend.put(entityId, spend / data.size());
            }
        }

        if (entityRoas.size() < 2) return;

        UUID bestEntity = entityRoas.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        UUID worstEntity = entityRoas.entrySet().stream()
                .min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        if (bestEntity == null || worstEntity == null || bestEntity.equals(worstEntity)) return;

        double bestRoas = entityRoas.get(bestEntity);
        double worstRoas = entityRoas.get(worstEntity);

        if (bestRoas > worstRoas * 2 && worstRoas < 2.0) {
            String payload = String.format(
                    "{\"from_entity\":\"%s\",\"to_entity\":\"%s\",\"from_roas\":%.2f,\"to_roas\":%.2f,\"reallocation_percent\":15}",
                    worstEntity, bestEntity, worstRoas, bestRoas);

            findings.add(new RuleFinding("BUDGET_ADJUST", "HIGH", "CAMPAIGN", bestEntity,
                    String.format("Reallocate budget: entity with ROAS %.2f → entity with ROAS %.2f",
                            worstRoas, bestRoas),
                    payload, 0.70, 0, 7, 0, true));
        }
    }

    // ══════════════════════════════════════════
    // LLM ENRICHMENT
    // ══════════════════════════════════════════

    private String enrichWithLlm(RuleFinding finding, UUID agencyId, UUID clientId) {
        try {
            String sharedContext = aiContextBuilder.buildContext(agencyId, clientId);

            String systemPrompt = """
                    You are a performance marketing expert. Explain this optimization finding \
                    to an agency account manager in a clear, actionable way. \
                    Be specific about what they should do and why. \
                    Write 2-4 sentences. Be direct and professional. \
                    Language: Use the same language as the client name suggests \
                    (Bulgarian if Cyrillic, English otherwise).\
                    """;

            String userMessage = String.format(
            "Shared context:\n%s\n\nFinding type: %s\nRisk: %s\nScope: %s\nDetails: %s\nData: %s",
            sharedContext, finding.suggestionType(), finding.riskLevel(),
                    finding.scopeType() + " " + finding.scopeId(),
                    finding.rawRationale(), finding.payloadJson());

            var response = claudeClient.sendMessage(
                    systemPrompt, userMessage, "OPTIMIZER_ENRICHMENT", agencyId, clientId);

            if (response.isSuccess()) {
                return response.text();
            }
        } catch (Exception e) {
            log.warn("LLM enrichment failed, using raw rationale: {}", e.getMessage());
        }
        return finding.rawRationale(); // Fallback
    }

    // ══════════════════════════════════════════
    // SAVE SUGGESTION
    // ══════════════════════════════════════════

    private AiSuggestion saveSuggestion(RuleFinding finding, String enrichedRationale,
                                        CompletableFuture<AiCrossModuleSupportService.CreativeSuggestionEnrichment> creativeEnrichmentFuture,
                                        UUID agencyId, UUID clientId) {
        AiCrossModuleSupportService.CreativeSuggestionEnrichment creativeEnrichment = creativeEnrichmentFuture.join();
        String finalRationale = enrichedRationale + creativeEnrichment.rationaleSuffix();

        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setAgencyId(agencyId);
        suggestion.setClientId(clientId);
        suggestion.setScopeType(finding.scopeType());
        suggestion.setScopeId(finding.scopeId());
        suggestion.setSuggestionType(finding.suggestionType());
        suggestion.setPayloadJson(augmentPayload(finding.payloadJson(), creativeEnrichment.recommendedCreatives()));
        suggestion.setRationale(finalRationale);
        suggestion.setConfidence(BigDecimal.valueOf(finding.confidence()));
        suggestion.setRiskLevel(finding.riskLevel());
        suggestion.setStatus("PENDING");
        suggestion.setCreatedBy("AI");
        suggestion.setCreatedAt(OffsetDateTime.now());

        AiSuggestion saved = suggestionRepo.save(suggestion);
        log.info("Created {} suggestion for {} {} (confidence: {})",
                finding.suggestionType(), finding.scopeType(), finding.scopeId(), finding.confidence());
        return saved;
    }

    private boolean shouldAttachCreativeRecommendations(RuleFinding finding) {
        return isCopyRefreshFinding(finding)
                || containsPayloadToken(finding.payloadJson(), "frequency_fatigue")
                || containsPayloadToken(finding.payloadJson(), "ctr_erosion");
    }

    private boolean isCopyRefreshFinding(RuleFinding finding) {
        return "COPY_REFRESH".equalsIgnoreCase(finding.suggestionType())
                || containsPayloadToken(finding.payloadJson(), "copy_refresh");
    }

    private boolean containsPayloadToken(String payloadJson, String token) {
        return payloadJson != null && payloadJson.contains(token);
    }

    private String augmentPayload(String payloadJson, List<Map<String, Object>> recommendedCreatives) {
        if (recommendedCreatives == null || recommendedCreatives.isEmpty()) {
            return payloadJson;
        }
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(payloadJson);
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (Map<String, Object> item : recommendedCreatives) {
                arrayNode.add(objectMapper.valueToTree(item));
            }
            node.set("recommended_creatives", arrayNode);
            return node.toString();
        } catch (Exception e) {
            log.warn("Failed to augment optimizer payload with recommended creatives: {}", e.getMessage());
            return payloadJson;
        }
    }

    // ══════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════

    private double avgCtr(List<InsightDaily> data) {
        return data.stream()
                .mapToDouble(i -> i.getCtr() != null ? i.getCtr().doubleValue() : 0)
                .average().orElse(0);
    }

    private double sumSpend(List<InsightDaily> data) {
        return data.stream()
                .mapToDouble(i -> i.getSpend() != null ? i.getSpend().doubleValue() : 0)
                .sum();
    }

    private long sumConversions(List<InsightDaily> data) {
        return data.stream()
                .mapToLong(i -> i.getConversions() != null ? i.getConversions().longValue() : 0)
                .sum();
    }

    private double sumConversionValue(List<InsightDaily> data) {
        return data.stream()
                .mapToDouble(i -> i.getConversionValue() != null ? i.getConversionValue().doubleValue() : 0)
                .sum();
    }

    private record AnalysisResult(int findingsCount, List<AiSuggestion> createdSuggestions) {}
}
