package com.amp.reports;

import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.tenancy.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for report and feedback operations.
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;
    private final AccessControl accessControl;

    public ReportController(ReportService reportService, AccessControl accessControl) {
        this.reportService = reportService;
        this.accessControl = accessControl;
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
}
