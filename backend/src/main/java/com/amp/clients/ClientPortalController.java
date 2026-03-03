package com.amp.clients;

import com.amp.ai.AiSuggestionService;
import com.amp.ai.SuggestionResponse;
import com.amp.campaigns.CampaignResponse;
import com.amp.campaigns.CampaignService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.insights.InsightService;
import com.amp.insights.KpiSummary;
import com.amp.reports.CreateFeedbackRequest;
import com.amp.reports.FeedbackResponse;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private final CampaignService campaignService;
    private final AiSuggestionService suggestionService;

    public ClientPortalController(ClientService clientService,
                                  ClientProfileService profileService,
                                  ReportService reportService,
                                  InsightService insightService,
                                  CampaignService campaignService,
                                  AiSuggestionService suggestionService) {
        this.clientService = clientService;
        this.profileService = profileService;
        this.reportService = reportService;
        this.insightService = insightService;
        this.campaignService = campaignService;
        this.suggestionService = suggestionService;
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

    // ── Dashboard KPIs ──────────────────────────────────────────

    @GetMapping("/dashboard/kpis")
    public ResponseEntity<KpiSummary> dashboardKpis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        TenantContext ctx = requireClientUser();
        KpiSummary kpis = insightService.getClientKpis(ctx.getAgencyId(), ctx.getClientId(), from, to);
        return ResponseEntity.ok(kpis);
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
}
