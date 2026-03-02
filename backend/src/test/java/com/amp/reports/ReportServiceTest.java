package com.amp.reports;

import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final UUID AGENCY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID REPORT_ID = UUID.randomUUID();

    @Mock private ReportRepository reportRepository;
    @Mock private FeedbackRepository feedbackRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private ReportService reportService;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.set(new TenantContext(AGENCY_ID, USER_ID, "test@local", "AGENCY_ADMIN"));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // ──────── helpers ────────

    private Report buildReport(String status) {
        Report r = new Report();
        r.setId(REPORT_ID);
        r.setAgencyId(AGENCY_ID);
        r.setClientId(CLIENT_ID);
        r.setReportType("MONTHLY");
        r.setPeriodStart(LocalDate.of(2026, 1, 1));
        r.setPeriodEnd(LocalDate.of(2026, 1, 31));
        r.setStatus(status);
        r.setHtmlContent("<h1>Test Report</h1>");
        r.setCreatedBy(USER_ID);
        r.setCreatedAt(OffsetDateTime.now());
        return r;
    }

    // ──────── listReports ────────

    @Test
    @DisplayName("listReports — returns all for client")
    void getReports_byClientId() {
        when(reportRepository.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(buildReport("DRAFT"), buildReport("SENT")));

        List<ReportResponse> result = reportService.listReports(AGENCY_ID, CLIENT_ID);

        assertThat(result).hasSize(2);
    }

    // ──────── generateReport ────────

    @Test
    @DisplayName("generateReport — success: status DRAFT, htmlContent not null, save called")
    void generateReport_success() {
        GenerateReportRequest req = new GenerateReportRequest(
                CLIENT_ID, "MONTHLY", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(REPORT_ID);
            return r;
        });

        ReportResponse result = reportService.generateReport(AGENCY_ID, req);

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.htmlContent()).isNotNull();
        verify(reportRepository).save(any(Report.class));
        verify(auditService).log(eq(AGENCY_ID), eq(CLIENT_ID), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ──────── markReportSent ────────

    @Test
    @DisplayName("markReportSent — success: APPROVED → SENT, sentAt not null")
    void sendReport_success() {
        Report report = buildReport("APPROVED");
        when(reportRepository.findByIdAndAgencyId(REPORT_ID, AGENCY_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportResponse result = reportService.markReportSent(AGENCY_ID, REPORT_ID);

        assertThat(result.status()).isEqualTo("SENT");
        assertThat(result.sentAt()).isNotNull();
    }

    @Test
    @DisplayName("markReportSent — DRAFT: service currently allows it (no guard)")
    void sendReport_notApproved() {
        // The current service does NOT guard against sending a DRAFT report;
        // it unconditionally sets SENT. If a guard is added, update this test.
        Report report = buildReport("DRAFT");
        when(reportRepository.findByIdAndAgencyId(REPORT_ID, AGENCY_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportResponse result = reportService.markReportSent(AGENCY_ID, REPORT_ID);

        assertThat(result.status()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("markReportSent — not found: throws ResourceNotFoundException")
    void sendReport_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(reportRepository.findByIdAndAgencyId(unknownId, AGENCY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.markReportSent(AGENCY_ID, unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ──────── createFeedback ────────

    @Test
    @DisplayName("createFeedback — success: save called, rating 5")
    void createFeedback_success() {
        UUID entityId = UUID.randomUUID();
        CreateFeedbackRequest req = new CreateFeedbackRequest(CLIENT_ID, "REPORT", entityId, 5, "Great report!");

        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(inv -> {
            Feedback f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });

        FeedbackResponse result = reportService.createFeedback(AGENCY_ID, req);

        assertThat(result.rating()).isEqualTo(5);
        assertThat(result.comment()).isEqualTo("Great report!");
        verify(feedbackRepository).save(any(Feedback.class));
    }
}
