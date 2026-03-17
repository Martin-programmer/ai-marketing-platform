package com.amp.insights;

import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.campaigns.CampaignPerformanceResponse;
import com.amp.campaigns.CampaignService;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Advanced KPI endpoints: period summary with comparison, daily trends for
 * charting, and top-campaigns ranking.
 * <p>
 * The existing {@code GET /clients/{id}/kpis} in {@link InsightController}
 * stays as-is; these endpoints add richer analytics under the same path prefix.
 */
@RestController
@RequestMapping("/api/v1")
public class KpiController {

    private final InsightDailyRepository insightRepo;
    private final AccessControl accessControl;
    private final CampaignService campaignService;

    public KpiController(InsightDailyRepository insightRepo, AccessControl accessControl,
                         CampaignService campaignService) {
        this.insightRepo = insightRepo;
        this.accessControl = accessControl;
        this.campaignService = campaignService;
    }

    private UUID agencyId() {
        return TenantContextHolder.require().getAgencyId();
    }

    // ── 1. KPI Summary with period-over-period comparison ────────────

    /**
     * GET /api/v1/clients/{clientId}/kpis/summary?from=...&amp;to=...
     * <p>
     * Returns aggregated KPIs for the requested period plus the same-length
     * previous period, together with percentage changes.
     */
    @GetMapping("/clients/{clientId}/kpis/summary")
    public ResponseEntity<?> kpiSummary(
            @PathVariable UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_VIEW);
        UUID agency = agencyId();

        // Current period
        KpiSummary current = insightRepo.aggregateKpis(agency, clientId, from, to);

        // Previous period (same duration, immediately before)
        long days = ChronoUnit.DAYS.between(from, to);
        LocalDate prevFrom = from.minusDays(days);
        LocalDate prevTo = from.minusDays(1);
        KpiSummary previous = insightRepo.aggregateKpis(agency, clientId, prevFrom, prevTo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", Map.of("from", from, "to", to));
        result.put("previousPeriod", Map.of("from", prevFrom, "to", prevTo));
        result.put("current", kpiToMap(current));
        result.put("previous", kpiToMap(previous));
        result.put("changes", calculateChanges(current, previous));

        return ResponseEntity.ok(result);
    }

    // ── 2. Daily trend data for charts ───────────────────────────────

    /**
     * GET /api/v1/clients/{clientId}/kpis/daily?from=...&amp;to=...
     * <p>
     * Returns one object per day with aggregated metrics (all entity types
     * summed) — suitable for line/bar charts.
     */
    @GetMapping("/clients/{clientId}/kpis/daily")
    public ResponseEntity<?> kpiDaily(
            @PathVariable UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_VIEW);
        UUID agency = agencyId();

        List<InsightDaily> insights = insightRepo.findAllByAgencyIdAndClientIdAndDateBetween(
                agency, clientId, from, to);

        // Aggregate by date (sum all entities per day) — TreeMap keeps ASC order
        Map<LocalDate, DailyAggregate> byDate = new TreeMap<>();
        for (InsightDaily i : insights) {
            byDate.computeIfAbsent(i.getDate(), d -> new DailyAggregate()).add(i);
        }

        List<Map<String, Object>> dailyData = new ArrayList<>();
        for (Map.Entry<LocalDate, DailyAggregate> entry : byDate.entrySet()) {
            DailyAggregate agg = entry.getValue();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", entry.getKey().toString());
            day.put("spend", agg.spend.setScale(2, RoundingMode.HALF_UP));
            day.put("impressions", agg.impressions);
            day.put("clicks", agg.clicks);
            day.put("conversions", agg.conversions.setScale(2, RoundingMode.HALF_UP));
            day.put("conversionValue", agg.conversionValue.setScale(2, RoundingMode.HALF_UP));
            day.put("ctr", agg.impressions > 0
                    ? BigDecimal.valueOf(agg.clicks)
                        .divide(BigDecimal.valueOf(agg.impressions), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO);
            day.put("cpc", agg.clicks > 0
                    ? agg.spend.divide(BigDecimal.valueOf(agg.clicks), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            day.put("cpm", agg.impressions > 0
                ? agg.spend.multiply(BigDecimal.valueOf(1000))
                .divide(BigDecimal.valueOf(agg.impressions), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
            day.put("roas", agg.spend.compareTo(BigDecimal.ZERO) > 0
                ? agg.conversionValue.divide(agg.spend, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
            day.put("reach", agg.reach);
            day.put("frequency", agg.reach > 0
                ? BigDecimal.valueOf(agg.impressions)
                .divide(BigDecimal.valueOf(agg.reach), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
            dailyData.add(day);
        }

        return ResponseEntity.ok(dailyData);
    }

    // ── 3. Top campaigns/entities by spend ───────────────────────────

    /**
     * GET /api/v1/clients/{clientId}/kpis/top-campaigns?from=...&amp;to=...&amp;limit=10
     * <p>
     * Returns entities ranked by spend descending. Works for any entity type
     * (CAMPAIGN, ADSET, AD).
     */
    @GetMapping("/clients/{clientId}/kpis/top-campaigns")
    public ResponseEntity<?> topCampaigns(
            @PathVariable UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {

        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_VIEW);
        UUID agency = agencyId();

        List<CampaignPerformanceResponse> topList = campaignService
            .listCampaignPerformance(agency, clientId, from, to)
            .stream()
            .limit(limit)
            .toList();

        return ResponseEntity.ok(topList);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> kpiToMap(KpiSummary k) {
        if (k == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalSpend", k.getTotalSpend() != null ? k.getTotalSpend() : BigDecimal.ZERO);
        m.put("totalImpressions", k.getTotalImpressions() != null ? k.getTotalImpressions() : 0L);
        m.put("totalClicks", k.getTotalClicks() != null ? k.getTotalClicks() : 0L);
        m.put("totalConversions", k.getTotalConversions() != null ? k.getTotalConversions() : BigDecimal.ZERO);
        m.put("avgCtr", k.getAvgCtr() != null ? k.getAvgCtr() : 0.0);
        m.put("avgCpc", k.getAvgCpc() != null ? k.getAvgCpc() : BigDecimal.ZERO);
        m.put("avgRoas", k.getAvgRoas() != null ? k.getAvgRoas() : BigDecimal.ZERO);
        return m;
    }

    private Map<String, Object> calculateChanges(KpiSummary current, KpiSummary previous) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("spend", pctChange(
                current != null ? current.getTotalSpend() : null,
                previous != null ? previous.getTotalSpend() : null));
        changes.put("impressions", pctChange(
                toBd(current != null ? current.getTotalImpressions() : null),
                toBd(previous != null ? previous.getTotalImpressions() : null)));
        changes.put("clicks", pctChange(
                toBd(current != null ? current.getTotalClicks() : null),
                toBd(previous != null ? previous.getTotalClicks() : null)));
        changes.put("conversions", pctChange(
                current != null ? current.getTotalConversions() : null,
                previous != null ? previous.getTotalConversions() : null));
        changes.put("ctr", pctChange(
                current != null && current.getAvgCtr() != null ? BigDecimal.valueOf(current.getAvgCtr()) : null,
                previous != null && previous.getAvgCtr() != null ? BigDecimal.valueOf(previous.getAvgCtr()) : null));
        changes.put("cpc", pctChange(
                current != null ? current.getAvgCpc() : null,
                previous != null ? previous.getAvgCpc() : null));
        return changes;
    }

    private static BigDecimal toBd(Long v) {
        return v != null ? BigDecimal.valueOf(v) : null;
    }

    private static Double pctChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    // ── Inner aggregation classes ────────────────────────────────────

    private static class DailyAggregate {
        BigDecimal spend = BigDecimal.ZERO;
        long impressions;
        long clicks;
        BigDecimal conversions = BigDecimal.ZERO;
        BigDecimal conversionValue = BigDecimal.ZERO;
        long reach;

        void add(InsightDaily i) {
            spend = spend.add(i.getSpend() != null ? i.getSpend() : BigDecimal.ZERO);
            impressions += i.getImpressions();
            clicks += i.getClicks();
            conversions = conversions.add(i.getConversions() != null ? i.getConversions() : BigDecimal.ZERO);
            conversionValue = conversionValue.add(i.getConversionValue() != null ? i.getConversionValue() : BigDecimal.ZERO);
            reach += i.getReach();
        }
    }

}
