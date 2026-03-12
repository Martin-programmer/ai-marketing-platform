package com.amp.ai;

import com.amp.ai.ClaudeApiClient.ClaudeResponse;
import com.amp.campaigns.Campaign;
import com.amp.campaigns.CampaignRepository;
import com.amp.campaigns.Adset;
import com.amp.campaigns.AdsetRepository;
import com.amp.clients.ClientRepository;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced budget analysis: pacing, day-of-week optimization, cross-campaign
 * rebalancing, and diminishing-returns detection.
 * <p>
 * Numbers are calculated in Java; only the narrative summary uses Claude.
 */
@Service
public class BudgetStrategistService {

    private static final Logger log = LoggerFactory.getLogger(BudgetStrategistService.class);
    private static final String MODULE = "BUDGET_STRATEGIST";

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;
    private final ClientRepository clientRepo;
    private final CampaignRepository campaignRepo;
    private final AdsetRepository adsetRepo;
    private final InsightDailyRepository insightRepo;
    private final AiContextBuilder aiContextBuilder;
    private final AiBudgetAnalysisRepository aiBudgetAnalysisRepository;
    private final ObjectMapper objectMapper;

    public BudgetStrategistService(ClaudeApiClient claudeClient,
                                    AiProperties aiProps,
                                    ClientRepository clientRepo,
                                    CampaignRepository campaignRepo,
                                    AdsetRepository adsetRepo,
                                    InsightDailyRepository insightRepo,
                                    AiContextBuilder aiContextBuilder,
                                    AiBudgetAnalysisRepository aiBudgetAnalysisRepository,
                                    ObjectMapper objectMapper) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.clientRepo = clientRepo;
        this.campaignRepo = campaignRepo;
        this.adsetRepo = adsetRepo;
        this.insightRepo = insightRepo;
        this.aiContextBuilder = aiContextBuilder;
        this.aiBudgetAnalysisRepository = aiBudgetAnalysisRepository;
        this.objectMapper = objectMapper;
    }

    // ══════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════

    @Transactional
    public AiBudgetAnalysis analyzeBudget(UUID agencyId, UUID clientId) {
        clientRepo.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        LocalDate today = LocalDate.now();
        LocalDate from30 = today.minusDays(30);
        List<InsightDaily> insights = insightRepo.findAllByAgencyIdAndClientIdAndDateBetween(
                agencyId, clientId, from30, today);

        if (insights.isEmpty()) {
            throw new IllegalStateException("No insight data for the last 30 days");
        }

        // Compute each analysis section
        Map<String, Object> pacing = computePacing(insights, today);
        Map<String, Object> dayOfWeek = computeDayOfWeek(insights);
        List<Map<String, Object>> campaignRanking = computeCampaignRanking(agencyId, clientId, insights);
        List<Map<String, Object>> diminishingReturns = computeDiminishingReturns(insights);

        // Build summary for LLM narrative
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("pacing", pacing);
        analysis.put("dayOfWeek", dayOfWeek);
        analysis.put("campaignRanking", campaignRanking);
        analysis.put("diminishingReturns", diminishingReturns);

        // Get AI narrative
        String narrative = generateNarrative(analysis, agencyId, clientId);
        analysis.put("narrative", narrative);

        try {
            AiBudgetAnalysis saved = new AiBudgetAnalysis();
            saved.setAgencyId(agencyId);
            saved.setClientId(clientId);
            saved.setAnalysisJson(objectMapper.writeValueAsString(analysis));
            saved.setCreatedAt(java.time.OffsetDateTime.now());
            return aiBudgetAnalysisRepository.save(saved);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist budget analysis", e);
        }
    }

    // ══════════════════════════════════════════
    // PACING ANALYSIS
    // ══════════════════════════════════════════

    private Map<String, Object> computePacing(List<InsightDaily> insights, LocalDate today) {
        // Calculate total budget from adset daily budgets × 30
        // We use the actual 30-day spend as the baseline
        double totalSpend30d = insights.stream()
                .mapToDouble(i -> dbl(i.getSpend())).sum();

        // Days elapsed in current month
        int dayOfMonth = today.getDayOfMonth();
        int daysInMonth = today.lengthOfMonth();
        double pctMonthElapsed = (double) dayOfMonth / daysInMonth * 100;

        // Current month spend only
        LocalDate monthStart = today.withDayOfMonth(1);
        double currentMonthSpend = insights.stream()
                .filter(i -> !i.getDate().isBefore(monthStart))
                .mapToDouble(i -> dbl(i.getSpend())).sum();

        // Projected month-end spend (linear projection)
        double dailyAvgThisMonth = dayOfMonth > 0 ? currentMonthSpend / dayOfMonth : 0;
        double projectedMonthSpend = dailyAvgThisMonth * daysInMonth;

        // Compare with last 30d run rate
        double dailyAvg30d = totalSpend30d / 30.0;
        double projectedFromRunRate = dailyAvg30d * daysInMonth;

        // Pacing status
        String pacingStatus;
        double pacingRatio = dayOfMonth > 0 && projectedMonthSpend > 0
                ? (currentMonthSpend / projectedMonthSpend) * 100 : 0;
        if (pctMonthElapsed > 0 && Math.abs(pacingRatio - pctMonthElapsed) < 10) {
            pacingStatus = "ON_TRACK";
        } else if (pacingRatio > pctMonthElapsed) {
            pacingStatus = "OVERSPENDING";
        } else {
            pacingStatus = "UNDERSPENDING";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSpend30d", round(totalSpend30d));
        result.put("currentMonthSpend", round(currentMonthSpend));
        result.put("dailyAvg30d", round(dailyAvg30d));
        result.put("dailyAvgThisMonth", round(dailyAvgThisMonth));
        result.put("projectedMonthSpend", round(projectedMonthSpend));
        result.put("daysInMonth", daysInMonth);
        result.put("dayOfMonth", dayOfMonth);
        result.put("pctMonthElapsed", round(pctMonthElapsed));
        result.put("pacingStatus", pacingStatus);
        return result;
    }

    // ══════════════════════════════════════════
    // DAY-OF-WEEK ANALYSIS
    // ══════════════════════════════════════════

    private Map<String, Object> computeDayOfWeek(List<InsightDaily> insights) {
        // Group insights by day-of-week
        Map<DayOfWeek, List<InsightDaily>> byDow = insights.stream()
                .collect(Collectors.groupingBy(i -> i.getDate().getDayOfWeek()));

        List<Map<String, Object>> days = new ArrayList<>();
        DayOfWeek bestDay = null;
        double bestRoas = -1;
        DayOfWeek worstDay = null;
        double worstRoas = Double.MAX_VALUE;

        for (DayOfWeek dow : DayOfWeek.values()) {
            List<InsightDaily> dayInsights = byDow.getOrDefault(dow, List.of());
            if (dayInsights.isEmpty()) {
                days.add(Map.of("day", dow.name(), "spend", 0, "conversions", 0, "roas", 0, "ctr", 0));
                continue;
            }

            double spend = dayInsights.stream().mapToDouble(i -> dbl(i.getSpend())).sum();
            double conversions = dayInsights.stream().mapToDouble(i -> dbl(i.getConversions())).sum();
            double convValue = dayInsights.stream().mapToDouble(i -> dbl(i.getConversionValue())).sum();
            double roas = spend > 0 ? convValue / spend : 0;
            double ctr = dayInsights.stream().mapToDouble(i -> dbl(i.getCtr())).average().orElse(0);

            Map<String, Object> dayMap = new LinkedHashMap<>();
            dayMap.put("day", dow.name());
            dayMap.put("spend", round(spend));
            dayMap.put("conversions", round(conversions));
            dayMap.put("roas", round(roas));
            dayMap.put("ctr", round(ctr));
            days.add(dayMap);

            if (spend > 0) {
                if (roas > bestRoas) { bestRoas = roas; bestDay = dow; }
                if (roas < worstRoas) { worstRoas = roas; worstDay = dow; }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("bestDay", bestDay != null ? bestDay.name() : null);
        result.put("bestDayRoas", round(bestRoas >= 0 ? bestRoas : 0));
        result.put("worstDay", worstDay != null ? worstDay.name() : null);
        result.put("worstDayRoas", round(worstRoas < Double.MAX_VALUE ? worstRoas : 0));
        result.put("recommendation", bestDay != null && worstDay != null
                ? "Shift budget from " + worstDay.name() + " to " + bestDay.name()
                : "Insufficient data for day-of-week recommendation");
        return result;
    }

    // ══════════════════════════════════════════
    // CAMPAIGN RANKING (ROAS-based)
    // ══════════════════════════════════════════

    private List<Map<String, Object>> computeCampaignRanking(UUID agencyId, UUID clientId,
                                                              List<InsightDaily> insights) {
        // Filter to CAMPAIGN-level insights
        Map<UUID, List<InsightDaily>> byCampaign = insights.stream()
                .filter(i -> "CAMPAIGN".equals(i.getEntityType()))
                .collect(Collectors.groupingBy(InsightDaily::getEntityId));

        List<Campaign> campaigns = campaignRepo.findAllByAgencyIdAndClientId(agencyId, clientId);
        Map<UUID, Campaign> campaignMap = campaigns.stream()
                .collect(Collectors.toMap(Campaign::getId, c -> c));

        // Compute total budget from adsets per campaign
        Map<UUID, BigDecimal> campaignBudgets = new HashMap<>();
        for (Campaign c : campaigns) {
            List<Adset> adsets = adsetRepo.findAllByCampaignId(c.getId());
            BigDecimal totalDaily = adsets.stream()
                    .map(Adset::getDailyBudget)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            campaignBudgets.put(c.getId(), totalDaily);
        }

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Map.Entry<UUID, List<InsightDaily>> entry : byCampaign.entrySet()) {
            UUID cid = entry.getKey();
            List<InsightDaily> cInsights = entry.getValue();
            Campaign campaign = campaignMap.get(cid);

            double spend = cInsights.stream().mapToDouble(i -> dbl(i.getSpend())).sum();
            double conversions = cInsights.stream().mapToDouble(i -> dbl(i.getConversions())).sum();
            double convValue = cInsights.stream().mapToDouble(i -> dbl(i.getConversionValue())).sum();
            double roas = spend > 0 ? convValue / spend : 0;
            BigDecimal dailyBudget = campaignBudgets.getOrDefault(cid, BigDecimal.ZERO);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("campaignId", cid.toString());
            item.put("campaignName", campaign != null ? campaign.getName() : "Unknown");
            item.put("status", campaign != null ? campaign.getStatus() : "UNKNOWN");
            item.put("spend30d", round(spend));
            item.put("conversions30d", round(conversions));
            item.put("roas30d", round(roas));
            item.put("dailyBudget", dailyBudget);

            // Suggest budget action
            if (roas > 3.0) {
                item.put("suggestion", "INCREASE_BUDGET");
                item.put("reason", "High ROAS — scaling opportunity");
            } else if (roas > 0 && roas < 1.0 && spend > 50) {
                item.put("suggestion", "DECREASE_BUDGET");
                item.put("reason", "Low ROAS — reduce waste");
            } else if (conversions == 0 && spend > 100) {
                item.put("suggestion", "PAUSE_OR_RESTRUCTURE");
                item.put("reason", "Zero conversions with significant spend");
            } else {
                item.put("suggestion", "MAINTAIN");
                item.put("reason", "Performance within acceptable range");
            }

            ranking.add(item);
        }

        // Sort by ROAS descending
        ranking.sort((a, b) -> Double.compare(
                ((Number) b.get("roas30d")).doubleValue(),
                ((Number) a.get("roas30d")).doubleValue()));
        return ranking;
    }

    // ══════════════════════════════════════════
    // DIMINISHING RETURNS DETECTION
    // ══════════════════════════════════════════

    private List<Map<String, Object>> computeDiminishingReturns(List<InsightDaily> insights) {
        // Group by entity, then sort by date to detect trend
        Map<String, List<InsightDaily>> byEntity = insights.stream()
                .filter(i -> "CAMPAIGN".equals(i.getEntityType()) || "ADSET".equals(i.getEntityType()))
                .collect(Collectors.groupingBy(i -> i.getEntityType() + ":" + i.getEntityId()));

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map.Entry<String, List<InsightDaily>> entry : byEntity.entrySet()) {
            List<InsightDaily> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(InsightDaily::getDate))
                    .toList();

            if (sorted.size() < 14) continue;

            // Split into first half and second half
            int mid = sorted.size() / 2;
            List<InsightDaily> firstHalf = sorted.subList(0, mid);
            List<InsightDaily> secondHalf = sorted.subList(mid, sorted.size());

            double spendFirst = firstHalf.stream().mapToDouble(i -> dbl(i.getSpend())).sum();
            double spendSecond = secondHalf.stream().mapToDouble(i -> dbl(i.getSpend())).sum();
            double convFirst = firstHalf.stream().mapToDouble(i -> dbl(i.getConversions())).sum();
            double convSecond = secondHalf.stream().mapToDouble(i -> dbl(i.getConversions())).sum();

            if (spendFirst <= 0 || convFirst <= 0) continue;

            double cpaFirst = spendFirst / convFirst;
            double cpaSecond = convSecond > 0 ? spendSecond / convSecond : spendSecond;
            double cpaDelta = cpaFirst > 0 ? (cpaSecond - cpaFirst) / cpaFirst : 0;
            double spendDelta = (spendSecond - spendFirst) / spendFirst;

            // Diminishing returns: spend increased but CPA also increased
            if (spendDelta > 0.10 && cpaDelta > 0.20) {
                String[] parts = entry.getKey().split(":");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("entityType", parts[0]);
                item.put("entityId", parts[1]);
                item.put("spendFirst", round(spendFirst));
                item.put("spendSecond", round(spendSecond));
                item.put("spendIncreasePct", round(spendDelta * 100));
                item.put("cpaFirst", round(cpaFirst));
                item.put("cpaSecond", round(cpaSecond));
                item.put("cpaIncreasePct", round(cpaDelta * 100));
                item.put("signal", "DIMINISHING_RETURNS");
                item.put("description", String.format(
                        "Spend increased %.0f%% but CPA increased %.0f%% — diminishing returns detected",
                        spendDelta * 100, cpaDelta * 100));
                results.add(item);
            }
        }
        return results;
    }

    // ══════════════════════════════════════════
    // LLM NARRATIVE
    // ══════════════════════════════════════════

    private String generateNarrative(Map<String, Object> analysis, UUID agencyId, UUID clientId) {
        try {
            String dataSummary = objectMapper.writeValueAsString(analysis);
            String sharedContext = aiContextBuilder.buildContext(agencyId, clientId);
            // Truncate if too long for the prompt
            if (dataSummary.length() > 6000) {
                dataSummary = dataSummary.substring(0, 6000) + "... (truncated)";
            }

            String systemPrompt = """
                    You are a senior media buyer reviewing a client's budget performance data.
                    Based on the analysis provided, write a concise 3-5 paragraph strategic recommendation.
                    Cover: pacing, day-of-week optimization, campaign rebalancing, and diminishing returns.
                    Be specific with numbers. Use a professional but actionable tone.
                    Do NOT use markdown. Plain text only.
                    """;

            ClaudeResponse response = claudeClient.sendMessage(
                    systemPrompt,
                    sharedContext + "\n\nBudget Analysis Data:\n" + dataSummary,
                    MODULE, agencyId, clientId);

            if (response.isSuccess()) {
                return response.text();
            }
            log.warn("Budget Strategist narrative LLM failed: {}", response.error());
            return "AI narrative unavailable — review the numbers above.";
        } catch (Exception e) {
            log.warn("Budget Strategist narrative generation failed: {}", e.getMessage());
            return "AI narrative unavailable — review the numbers above.";
        }
    }

    // ══════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════

    private double dbl(BigDecimal val) {
        return val != null ? val.doubleValue() : 0.0;
    }

    private double round(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
