package com.amp.reports;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service handling report generation, lifecycle and feedback.
 */
@Service
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final FeedbackRepository feedbackRepository;
    private final AuditService auditService;

    public ReportService(ReportRepository reportRepository,
                         FeedbackRepository feedbackRepository,
                         AuditService auditService) {
        this.reportRepository = reportRepository;
        this.feedbackRepository = feedbackRepository;
        this.auditService = auditService;
    }

    // ──────── Report ────────

    public ReportResponse generateReport(UUID agencyId, GenerateReportRequest req) {
        TenantContext ctx = TenantContextHolder.require();

        Report r = new Report();
        r.setAgencyId(agencyId);
        r.setClientId(req.clientId());
        r.setReportType(req.reportType());
        r.setPeriodStart(req.periodStart());
        r.setPeriodEnd(req.periodEnd());
        r.setStatus("DRAFT");
        r.setHtmlContent("");
        r.setCreatedBy(ctx.getUserId());
        r.setCreatedAt(OffsetDateTime.now());

        Report saved = reportRepository.save(r);

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

    private Report findReportOrThrow(UUID agencyId, UUID reportId) {
        return reportRepository.findByIdAndAgencyId(reportId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", reportId));
    }
}
