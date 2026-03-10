package com.amp.reports;

import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for report, feedback, PDF/HTML and narrative operations.
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;
    private final AccessControl accessControl;
    private final PdfGenerator pdfGenerator;
    private final ReportRepository reportRepository;

    public ReportController(ReportService reportService,
                            AccessControl accessControl,
                            PdfGenerator pdfGenerator,
                            ReportRepository reportRepository) {
        this.reportService = reportService;
        this.accessControl = accessControl;
        this.pdfGenerator = pdfGenerator;
        this.reportRepository = reportRepository;
    }

    // ──────── Report ────────

    @PostMapping("/clients/{clientId}/reports/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse generateReport(@PathVariable UUID clientId,
                                         @Valid @RequestBody GenerateReportRequest req) {
        accessControl.requireClientPermission(clientId, Permission.REPORTS_EDIT);
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        if (!clientId.equals(req.clientId())) {
            throw new IllegalArgumentException("clientId in path and body must match");
        }
        return reportService.generateReport(agencyId, req);
    }

    @GetMapping("/clients/{clientId}/reports")
    public List<ReportResponse> listReports(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.REPORTS_VIEW);
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return reportService.listReports(agencyId, clientId);
    }

    @GetMapping("/reports/{reportId}")
    public ReportResponse getReport(@PathVariable UUID reportId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = reportService.resolveClientId(agencyId, reportId);
        accessControl.requireClientPermission(clientId, Permission.REPORTS_VIEW);
        return reportService.getReport(agencyId, reportId);
    }

    @PostMapping("/reports/{reportId}/send")
    public ReportResponse markReportSent(@PathVariable UUID reportId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = reportService.resolveClientId(agencyId, reportId);
        accessControl.requireClientPermission(clientId, Permission.REPORTS_SEND);
        return reportService.markReportSent(agencyId, reportId);
    }

    // ──────── Feedback ────────

    @PostMapping("/clients/{clientId}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse createFeedback(@PathVariable UUID clientId,
                                           @Valid @RequestBody CreateFeedbackRequest req) {
        accessControl.requireClientPermission(clientId, Permission.REPORTS_EDIT);
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        if (!clientId.equals(req.clientId())) {
            throw new IllegalArgumentException("clientId in path and body must match");
        }
        return reportService.createFeedback(agencyId, req);
    }

    @GetMapping("/clients/{clientId}/feedback")
    public List<FeedbackResponse> listFeedback(
            @PathVariable UUID clientId,
            @RequestParam(required = false) String entityType) {
        accessControl.requireClientPermission(clientId, Permission.REPORTS_VIEW);
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return reportService.listFeedback(agencyId, clientId, entityType);
    }

    // ──────── PDF / HTML download ────────

    @GetMapping("/reports/{reportId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID reportId) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = reportService.resolveClientId(agencyId, reportId);
        accessControl.requireClientPermission(clientId, Permission.REPORTS_VIEW);

        Report report = reportService.getReportEntity(reportId);
        byte[] pdfBytes = pdfGenerator.generatePdf(report.getHtmlContent());

        String filename = String.format("report_%s_%s.pdf",
                report.getReportType().toLowerCase(),
                report.getPeriodEnd().toString());

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }

    @GetMapping("/reports/{reportId}/html")
    public ResponseEntity<String> viewHtml(@PathVariable UUID reportId) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = reportService.resolveClientId(agencyId, reportId);
        accessControl.requireClientPermission(clientId, Permission.REPORTS_VIEW);

        Report report = reportService.getReportEntity(reportId);
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(report.getHtmlContent());
    }

    // ──────── Narrative / Edit ────────

    @PatchMapping("/reports/{reportId}")
    public ResponseEntity<?> updateReport(@PathVariable UUID reportId,
                                          @RequestBody Map<String, String> request) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        UUID clientId = reportService.resolveClientId(agencyId, reportId);
        accessControl.requireClientPermission(clientId, Permission.REPORTS_EDIT);

        Report report = reportService.getReportEntity(reportId);

        if (!"DRAFT".equals(report.getStatus()) && !"IN_REVIEW".equals(report.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Can only edit DRAFT or IN_REVIEW reports"));
        }

        String narrative = request.get("narrative");
        if (narrative != null) {
            String newHtml = reportService.regenerateWithNarrative(
                    report, NarrativeSections.ofSummary(narrative));
            report.setHtmlContent(newHtml);
            reportRepository.save(report);
        }

        return ResponseEntity.ok(ReportResponse.from(report));
    }
}
