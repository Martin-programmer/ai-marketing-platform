package com.amp.reports;

import com.amp.agency.Agency;
import com.amp.agency.AgencyRepository;
import com.amp.ai.AiReporterService;
import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.common.EmailProperties;
import com.amp.common.NotificationHelper;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.amp.insights.KpiSummary;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service handling report generation, lifecycle and feedback.
 */
@Service
@Transactional
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final FeedbackRepository feedbackRepository;
    private final AuditService auditService;
    private final InsightDailyRepository insightDailyRepository;
    private final ClientRepository clientRepository;
    private final AgencyRepository agencyRepository;
    private final AiReporterService aiReporterService;
    private final NotificationHelper notificationHelper;
    private final EmailProperties emailProperties;

    public ReportService(ReportRepository reportRepository,
                         FeedbackRepository feedbackRepository,
                         AuditService auditService,
                         InsightDailyRepository insightDailyRepository,
                         ClientRepository clientRepository,
                         AgencyRepository agencyRepository,
                         AiReporterService aiReporterService,
                         NotificationHelper notificationHelper,
                         EmailProperties emailProperties) {
        this.reportRepository = reportRepository;
        this.feedbackRepository = feedbackRepository;
        this.auditService = auditService;
        this.insightDailyRepository = insightDailyRepository;
        this.clientRepository = clientRepository;
        this.agencyRepository = agencyRepository;
        this.aiReporterService = aiReporterService;
        this.notificationHelper = notificationHelper;
        this.emailProperties = emailProperties;
    }

    // ──────── Report ────────

    public ReportResponse generateReport(UUID agencyId, GenerateReportRequest req) {
        TenantContext ctx = TenantContextHolder.require();

        // Resolve names for branding
        String clientName = clientRepository.findByIdAndAgencyId(req.clientId(), agencyId)
                .map(Client::getName).orElse("Client");
        String agencyName = agencyRepository.findById(agencyId)
                .map(Agency::getName).orElse("Agency");

        // Fetch KPI data for report period
        KpiSummary current = insightDailyRepository.aggregateKpis(
                agencyId, req.clientId(), req.periodStart(), req.periodEnd());

        // Previous period of same duration for comparison
        long days = ChronoUnit.DAYS.between(req.periodStart(), req.periodEnd());
        KpiSummary previous = insightDailyRepository.aggregateKpis(
                agencyId, req.clientId(),
                req.periodStart().minusDays(days),
                req.periodStart().minusDays(1));

        // Top campaigns aggregation
        List<Map<String, Object>> topCampaigns = aggregateTopCampaigns(
                agencyId, req.clientId(), req.periodStart(), req.periodEnd(), 10);

        // Generate AI narrative (optional enhancement — falls back gracefully)
        NarrativeSections narrative = null;
        if (aiReporterService != null) {
            try {
                narrative = aiReporterService.generateNarrative(
                        agencyId, req.clientId(), req.periodStart(), req.periodEnd());
            } catch (Exception e) {
                log.warn("AI narrative generation failed, continuing without it: {}", e.getMessage());
            }
        }

        // Build professional HTML
        String htmlContent = ReportHtmlBuilder.buildHtml(
                clientName, agencyName,
                req.periodStart(), req.periodEnd(),
                current, previous,
                null, // dailyData not rendered in report HTML table (charts only in frontend)
                topCampaigns,
                narrative
        );

        Report r = new Report();
        r.setAgencyId(agencyId);
        r.setClientId(req.clientId());
        r.setReportType(req.reportType());
        r.setPeriodStart(req.periodStart());
        r.setPeriodEnd(req.periodEnd());
        r.setStatus("DRAFT");
        r.setHtmlContent(htmlContent);
        r.setCreatedBy(ctx.getUserId());
        r.setCreatedAt(OffsetDateTime.now());

        Report saved = reportRepository.save(r);

        log.info("Generated report {} for client {} ({} – {})",
                saved.getId(), req.clientId(), req.periodStart(), req.periodEnd());

        auditService.log(agencyId, req.clientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.REPORT_GENERATE, "Report", saved.getId(),
                null, saved, null);

        return ReportResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> listReports(UUID agencyId, UUID clientId) {
        return reportRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream().map(ReportResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID agencyId, UUID reportId) {
        Report r = findReportOrThrow(agencyId, reportId);
        return ReportResponse.from(r);
    }

    public ReportResponse markReportApproved(UUID agencyId, UUID reportId) {
        TenantContext ctx = TenantContextHolder.require();
        Report r = findReportOrThrow(agencyId, reportId);

        if (!"DRAFT".equals(r.getStatus()) && !"IN_REVIEW".equals(r.getStatus())) {
            throw new IllegalStateException("Only DRAFT or IN_REVIEW reports can be approved, current status: " + r.getStatus());
        }

        r.setStatus("APPROVED");
        r.setApprovedBy(ctx.getUserId());
        r.setApprovedAt(OffsetDateTime.now());
        Report saved = reportRepository.save(r);

        auditService.log(agencyId, r.getClientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.REPORT_APPROVE, "Report", reportId,
                null, "APPROVED", null);

        return ReportResponse.from(saved);
    }

    public ReportResponse markReportSent(UUID agencyId, UUID reportId) {
        TenantContext ctx = TenantContextHolder.require();
        Report r = findReportOrThrow(agencyId, reportId);

        if (!"APPROVED".equals(r.getStatus())) {
            throw new IllegalStateException("Report must be APPROVED to send, current status: " + r.getStatus());
        }

        r.setSentAt(OffsetDateTime.now());
        r.setStatus("SENT");
        Report saved = reportRepository.save(r);

        auditService.log(agencyId, r.getClientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.REPORT_SEND, "Report", reportId,
                null, "SENT", null);

        // Send report email to CLIENT_USERs
        try {
            sendReportNotifications(agencyId, r);
        } catch (Exception e) {
            log.warn("Failed to send report email notifications: {}", e.getMessage());
        }

        return ReportResponse.from(saved);
    }

    // ──────── Feedback ────────

    public FeedbackResponse createFeedback(UUID agencyId, CreateFeedbackRequest req) {
        TenantContext ctx = TenantContextHolder.require();

        Feedback f = new Feedback();
        f.setAgencyId(agencyId);
        f.setClientId(req.clientId());
        f.setSourceType(req.entityType());
        f.setSourceId(req.entityId());
        f.setRating(req.rating());
        f.setComment(req.comment());
        f.setCreatedBy(ctx.getUserId());
        f.setCreatedAt(OffsetDateTime.now());

        Feedback saved = feedbackRepository.save(f);
        return FeedbackResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> listFeedback(UUID agencyId, UUID clientId, String entityType) {
        List<Feedback> list;
        if (entityType == null || entityType.isBlank()) {
            list = feedbackRepository.findAllByAgencyIdAndClientId(agencyId, clientId);
        } else {
            list = feedbackRepository.findAllByAgencyIdAndClientIdAndSourceType(agencyId, clientId, entityType);
        }
        return list.stream().map(FeedbackResponse::from).toList();
    }

    // ──────── Helpers ────────

    /**
     * Resolves the clientId that owns a given report (used for permission checks).
     */
    @Transactional(readOnly = true)
    public UUID resolveClientId(UUID agencyId, UUID reportId) {
        return findReportOrThrow(agencyId, reportId).getClientId();
    }

    /**
     * Returns the raw Report entity for the current agency (used by PDF/HTML endpoints).
     */
    @Transactional(readOnly = true)
    public Report getReportEntity(UUID reportId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return findReportOrThrow(agencyId, reportId);
    }

    /**
     * Returns the raw Report entity scoped to a specific client (used by portal PDF endpoint).
     */
    @Transactional(readOnly = true)
    public Report getReportEntityForClient(UUID reportId, UUID clientId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        Report r = findReportOrThrow(agencyId, reportId);
        if (!clientId.equals(r.getClientId())) {
            throw new ResourceNotFoundException("Report", reportId);
        }
        return r;
    }

    /**
     * Regenerate the report HTML with an updated narrative and save it.
     */
    public String regenerateWithNarrative(Report report, NarrativeSections narrative) {
        UUID agencyId = report.getAgencyId();
        UUID clientId = report.getClientId();

        String clientName = clientRepository.findByIdAndAgencyId(clientId, agencyId)
                .map(Client::getName).orElse("Client");
        String agencyName = agencyRepository.findById(agencyId)
                .map(Agency::getName).orElse("Agency");

        KpiSummary current = insightDailyRepository.aggregateKpis(
                agencyId, clientId, report.getPeriodStart(), report.getPeriodEnd());

        long days = ChronoUnit.DAYS.between(report.getPeriodStart(), report.getPeriodEnd());
        KpiSummary previous = insightDailyRepository.aggregateKpis(
                agencyId, clientId,
                report.getPeriodStart().minusDays(days),
                report.getPeriodStart().minusDays(1));

        List<Map<String, Object>> topCampaigns = aggregateTopCampaigns(
                agencyId, clientId, report.getPeriodStart(), report.getPeriodEnd(), 10);

        return ReportHtmlBuilder.buildHtml(
                clientName, agencyName,
                report.getPeriodStart(), report.getPeriodEnd(),
                current, previous,
                null, topCampaigns,
                narrative);
    }

    // ──────── Email notifications ────────

    private void sendReportNotifications(UUID agencyId, Report report) {
        String clientName = clientRepository.findByIdAndAgencyId(report.getClientId(), agencyId)
                .map(Client::getName).orElse("Client");
        String period = report.getPeriodStart() + " – " + report.getPeriodEnd();

        // Aggregate KPIs for the email
        KpiSummary kpi = insightDailyRepository.aggregateKpis(
                agencyId, report.getClientId(), report.getPeriodStart(), report.getPeriodEnd());

        String spend = kpi != null && kpi.getTotalSpend() != null
                ? "$" + kpi.getTotalSpend().setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "$0.00";
        String conversions = kpi != null && kpi.getTotalConversions() != null
                ? kpi.getTotalConversions().setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "0";
        String roas = kpi != null && kpi.getAvgRoas() != null
                ? kpi.getAvgRoas().setScale(2, RoundingMode.HALF_UP).toPlainString() + "x"
                : "N/A";

        String portalLink = emailProperties.getBaseUrl() + "/portal/reports";
        String subject = "Performance Report — " + clientName + " (" + period + ")";

        Map<String, String> vars = Map.of(
                "clientName", clientName,
                "period", period,
                "spend", spend,
                "conversions", conversions,
                "roas", roas,
                "portalLink", portalLink
        );

        List<String> recipients = notificationHelper.getClientUserEmails(report.getClientId());
        for (String email : recipients) {
            notificationHelper.sendTemplatedAsync(email, subject, "report-sent", vars);
        }

        log.info("Queued report-sent email to {} CLIENT_USER(s) for client {}", recipients.size(), report.getClientId());
    }

    // ──────── Top campaigns aggregation ────────

    private List<Map<String, Object>> aggregateTopCampaigns(
            UUID agencyId, UUID clientId,
            java.time.LocalDate from, java.time.LocalDate to, int limit) {

        List<InsightDaily> insights = insightDailyRepository
                .findAllByAgencyIdAndClientIdAndDateBetween(agencyId, clientId, from, to);

        Map<String, CampaignAggregate> byEntity = new LinkedHashMap<>();
        for (InsightDaily i : insights) {
            String key = i.getEntityType() + ":" + i.getEntityId();
            byEntity.computeIfAbsent(key,
                    k -> new CampaignAggregate(i.getEntityType(), i.getEntityId())).add(i);
        }

        return byEntity.values().stream()
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

    private Report findReportOrThrow(UUID agencyId, UUID reportId) {
        return reportRepository.findByIdAndAgencyId(reportId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", reportId));
    }
}
