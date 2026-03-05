package com.amp.clients;

import com.amp.ai.AiSuggestionService;
import com.amp.ai.SuggestionResponse;
import com.amp.campaigns.CampaignResponse;
import com.amp.campaigns.CampaignService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.amp.insights.InsightService;
import com.amp.insights.KpiSummary;
import com.amp.reports.CreateFeedbackRequest;
import com.amp.reports.FeedbackResponse;
import com.amp.reports.PdfGenerator;
import com.amp.reports.Report;
import com.amp.reports.ReportResponse;
import com.amp.reports.ReportService;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Portal endpoints for CLIENT_USER role – read-only access to their
 * own client data plus the ability to leave feedback on reports.
 * <p>
 * All endpoints are under {@code /api/v1/portal}.
 */
@RestController
@RequestMapping("/api/v1/portal")
public class ClientPortalController {

    private static final Set<String> VISIBLE_REPORT_STATUSES = Set.of("SENT", "APPROVED");
    private static final Set<String> VISIBLE_CAMPAIGN_STATUSES_EXCLUDE = Set.of("DRAFT");
    private static final Set<String> VISIBLE_SUGGESTION_STATUSES = Set.of("APPLIED", "APPROVED");

    private final ClientService clientService;
    private final ClientProfileService profileService;
    private final ReportService reportService;
    private final InsightService insightService;
    private final InsightDailyRepository insightDailyRepository;
    private final CampaignService campaignService;
    private final AiSuggestionService suggestionService;
    private final PdfGenerator pdfGenerator;

    public ClientPortalController(ClientService clientService,
                                  ClientProfileService profileService,
                                  ReportService reportService,
                                  InsightService insightService,
                                  InsightDailyRepository insightDailyRepository,
                                  CampaignService campaignService,
                                  AiSuggestionService suggestionService,
                                  PdfGenerator pdfGenerator) {
        this.clientService = clientService;
        this.profileService = profileService;
        this.reportService = reportService;
        this.insightService = insightService;
        this.insightDailyRepository = insightDailyRepository;
        this.campaignService = campaignService;
        this.suggestionService = suggestionService;
        this.pdfGenerator = pdfGenerator;
    }

    // ── Request DTOs ────────────────────────────────────────────

    public record PortalFeedbackRequest(
            @NotNull(message = "rating is required") @Min(1) @Max(5) Integer rating,
            String comment) {}

    // ── Client info ─────────────────────────────────────────────

    @GetMapping("/me/client")
    public ResponseEntity<ClientResponse> myClient() {
        TenantContext ctx = requireClientUser();
        Client client = clientService.getClient(ctx.getAgencyId(), ctx.getClientId());
        return ResponseEntity.ok(ClientResponse.from(client));
    }

    @GetMapping("/me/client/profile")
    public ResponseEntity<ClientProfileResponse> myClientProfile() {
        TenantContext ctx = requireClientUser();
        return ResponseEntity.ok(ClientProfileResponse.from(profileService.getProfile(ctx.getClientId())));
    }

    // ── Reports ─────────────────────────────────────────────────

    @GetMapping("/reports")
    public ResponseEntity<List<ReportResponse>> listReports() {
        TenantContext ctx = requireClientUser();
        List<ReportResponse> reports = reportService.listReports(ctx.getAgencyId(), ctx.getClientId())
                .stream()
                .filter(r -> VISIBLE_REPORT_STATUSES.contains(r.status()))
                .toList();
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<ReportResponse> getReport(@PathVariable UUID reportId) {
        TenantContext ctx = requireClientUser();
        ReportResponse report = reportService.getReport(ctx.getAgencyId(), reportId);
        if (!ctx.getClientId().equals(report.clientId())) {
            throw new ResourceNotFoundException("Report", reportId);
        }
        if (!VISIBLE_REPORT_STATUSES.contains(report.status())) {
            throw new ResourceNotFoundException("Report", reportId);
        }
        return ResponseEntity.ok(report);
    }

    @PostMapping("/reports/{reportId}/feedback")
    public ResponseEntity<FeedbackResponse> addReportFeedback(
            @PathVariable UUID reportId,
            @Valid @RequestBody PortalFeedbackRequest req) {
        TenantContext ctx = requireClientUser();

        // Verify report belongs to client and is visible
        ReportResponse report = reportService.getReport(ctx.getAgencyId(), reportId);
        if (!ctx.getClientId().equals(report.clientId())) {
            throw new ResourceNotFoundException("Report", reportId);
        }
        if (!VISIBLE_REPORT_STATUSES.contains(report.status())) {
            throw new ResourceNotFoundException("Report", reportId);
        }

        CreateFeedbackRequest feedbackReq = new CreateFeedbackRequest(
                ctx.getClientId(), "REPORT", reportId, req.rating(), req.comment());
        FeedbackResponse feedback = reportService.createFeedback(ctx.getAgencyId(), feedbackReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(feedback);
    }

    // ── Report PDF download ─────────────────────────────────────

    @GetMapping("/reports/{reportId}/pdf")
    public ResponseEntity<byte[]> portalDownloadPdf(@PathVariable UUID reportId) {
        TenantContext ctx = requireClientUser();
        Report report = reportService.getReportEntityForClient(reportId, ctx.getClientId());

        if (!"SENT".equals(report.getStatus()) && report.getSentAt() == null) {
            throw new IllegalStateException("Report not yet available");
        }

        byte[] pdfBytes = pdfGenerator.generatePdf(report.getHtmlContent());
        String filename = String.format("report_%s.pdf", report.getPeriodEnd());

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }

    // ── Dashboard KPIs ──────────────────────────────────────────

    @GetMapping("/dashboard/kpis")
    public ResponseEntity<KpiSummary> dashboardKpis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        TenantContext ctx = requireClientUser();
        KpiSummary kpis = insightService.getClientKpis(ctx.getAgencyId(), ctx.getClientId(), from, to);
        return ResponseEntity.ok(kpis);
    }

    // ── Dashboard KPI Summary with period-over-period comparison ──

    @GetMapping("/dashboard/kpis/summary")
    public ResponseEntity<?> dashboardKpiSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        TenantContext ctx = requireClientUser();
        UUID agency = ctx.getAgencyId();
        UUID clientId = ctx.getClientId();

        KpiSummary current = insightDailyRepository.aggregateKpis(agency, clientId, from, to);

        long days = ChronoUnit.DAYS.between(from, to);
        LocalDate prevFrom = from.minusDays(days);
        LocalDate prevTo = from.minusDays(1);
        KpiSummary previous = insightDailyRepository.aggregateKpis(agency, clientId, prevFrom, prevTo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", Map.of("from", from, "to", to));
        result.put("previousPeriod", Map.of("from", prevFrom, "to", prevTo));
        result.put("current", kpiToMap(current));
        result.put("previous", kpiToMap(previous));
        result.put("changes", calculateChanges(current, previous));

        return ResponseEntity.ok(result);
    }

    // ── Dashboard Daily trend data for charts ─────────────────────

    @GetMapping("/dashboard/kpis/daily")
    public ResponseEntity<?> dashboardKpiDaily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        TenantContext ctx = requireClientUser();
        UUID agency = ctx.getAgencyId();
        UUID clientId = ctx.getClientId();

        List<InsightDaily> insights = insightDailyRepository.findAllByAgencyIdAndClientIdAndDateBetween(
                agency, clientId, from, to);

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
            day.put("ctr", agg.impressions > 0
                    ? BigDecimal.valueOf(agg.clicks)
                        .divide(BigDecimal.valueOf(agg.impressions), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO);
            day.put("cpc", agg.clicks > 0
                    ? agg.spend.divide(BigDecimal.valueOf(agg.clicks), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            dailyData.add(day);
        }

        return ResponseEntity.ok(dailyData);
    }

    // ── Dashboard Top campaigns by spend ──────────────────────────

    @GetMapping("/dashboard/kpis/top-campaigns")
    public ResponseEntity<?> dashboardTopCampaigns(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        TenantContext ctx = requireClientUser();
        UUID agency = ctx.getAgencyId();
        UUID clientId = ctx.getClientId();

        List<InsightDaily> insights = insightDailyRepository.findAllByAgencyIdAndClientIdAndDateBetween(
                agency, clientId, from, to);

        Map<String, CampaignAggregate> byEntity = new LinkedHashMap<>();
        for (InsightDaily i : insights) {
            String key = i.getEntityType() + ":" + i.getEntityId();
            byEntity.computeIfAbsent(key,
                    k -> new CampaignAggregate(i.getEntityType(), i.getEntityId())).add(i);
        }

        List<Map<String, Object>> topList = byEntity.values().stream()
                .sorted((a, b) -> b.spend.compareTo(a.spend))
                .limit(limit)
                .map(agg -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("entityType", agg.entityType);
                    m.put("entityId", agg.entityId);
                    m.put("spend", agg.spend.setScale(2, RoundingMode.HALF_UP));
                    m.put("impressions", agg.impressions);
                    m.put("clicks", agg.clicks);
                    m.put("conversions", agg.conversions.setScale(2, RoundingMode.HALF_UP));
                    m.put("ctr", agg.impressions > 0
                            ? BigDecimal.valueOf(agg.clicks)
                                .divide(BigDecimal.valueOf(agg.impressions), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
                    m.put("cpc", agg.clicks > 0
                            ? agg.spend.divide(BigDecimal.valueOf(agg.clicks), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
                    return m;
                })
                .toList();

        return ResponseEntity.ok(topList);
    }

    // ── Campaigns ───────────────────────────────────────────────

    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignResponse>> listCampaigns() {
        TenantContext ctx = requireClientUser();
        List<CampaignResponse> campaigns = campaignService.listCampaigns(ctx.getAgencyId(), ctx.getClientId())
                .stream()
                .map(CampaignResponse::from)
                .filter(c -> !VISIBLE_CAMPAIGN_STATUSES_EXCLUDE.contains(c.status()))
                .toList();
        return ResponseEntity.ok(campaigns);
    }

    // ── AI Suggestions ──────────────────────────────────────────

    @GetMapping("/suggestions")
    public ResponseEntity<List<SuggestionResponse>> listSuggestions() {
        TenantContext ctx = requireClientUser();
        List<SuggestionResponse> suggestions = suggestionService.listSuggestions(
                        ctx.getAgencyId(), ctx.getClientId(), null)
                .stream()
                .filter(s -> VISIBLE_SUGGESTION_STATUSES.contains(s.status()))
                .toList();
        return ResponseEntity.ok(suggestions);
    }

    // ── Helpers ─────────────────────────────────────────────────

    /**
     * Verifies the caller is a CLIENT_USER with a valid clientId.
     *
     * @return the current {@link TenantContext}
     * @throws AccessDeniedException if role is not CLIENT_USER or clientId is null
     */
    private TenantContext requireClientUser() {
        TenantContext ctx = TenantContextHolder.require();
        if (!"CLIENT_USER".equals(ctx.getRole())) {
            throw new AccessDeniedException(
                    "Portal endpoints are only accessible to CLIENT_USER role.");
        }
        if (ctx.getClientId() == null) {
            throw new AccessDeniedException(
                    "CLIENT_USER must have a clientId assigned.");
        }
        return ctx;
    }

    // ── KPI helpers ─────────────────────────────────────────────

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

    // ── Inner aggregation classes ────────────────────────────────

    private static class DailyAggregate {
        BigDecimal spend = BigDecimal.ZERO;
        long impressions;
        long clicks;
        BigDecimal conversions = BigDecimal.ZERO;

        void add(InsightDaily i) {
            spend = spend.add(i.getSpend() != null ? i.getSpend() : BigDecimal.ZERO);
            impressions += i.getImpressions();
            clicks += i.getClicks();
            conversions = conversions.add(i.getConversions() != null ? i.getConversions() : BigDecimal.ZERO);
        }
    }

    private static class CampaignAggregate {
        final String entityType;
        final UUID entityId;
        BigDecimal spend = BigDecimal.ZERO;
        long impressions;
        long clicks;
        BigDecimal conversions = BigDecimal.ZERO;

        CampaignAggregate(String entityType, UUID entityId) {
            this.entityType = entityType;
            this.entityId = entityId;
        }

        void add(InsightDaily i) {
            spend = spend.add(i.getSpend() != null ? i.getSpend() : BigDecimal.ZERO);
            impressions += i.getImpressions();
            clicks += i.getClicks();
            conversions = conversions.add(i.getConversions() != null ? i.getConversions() : BigDecimal.ZERO);
        }
    }
}
