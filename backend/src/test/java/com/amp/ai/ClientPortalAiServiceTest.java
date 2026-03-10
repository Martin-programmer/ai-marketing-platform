package com.amp.ai;

import com.amp.campaigns.Campaign;
import com.amp.campaigns.CampaignRepository;
import com.amp.insights.InsightDailyRepository;
import com.amp.insights.KpiSummary;
import com.amp.reports.Report;
import com.amp.reports.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientPortalAiServiceTest {

    private static final UUID AGENCY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Mock private ClaudeApiClient claudeClient;
    @Mock private AiProperties aiProps;
    @Mock private InsightDailyRepository insightDailyRepo;
    @Mock private CampaignRepository campaignRepo;
    @Mock private AiSuggestionRepository suggestionRepo;
    @Mock private ReportRepository reportRepo;

    @InjectMocks
    private ClientPortalAiService service;

    // ── answerQuestion ──

    @Test
    @DisplayName("answerQuestion — successful Claude call returns answer")
    void answerQuestion_success() {
        // Arrange: stub KPI data
        KpiSummary kpis = new KpiSummary(100_000L, 5_000L,
                new BigDecimal("2500.00"), new BigDecimal("150.00"),
                5.0, new BigDecimal("0.50"), new BigDecimal("3.50"));
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(kpis);
        when(campaignRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());
        when(suggestionRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());
        when(reportRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());

        var claudeResponse = new ClaudeApiClient.ClaudeResponse(
                "Your campaigns spent $2,500 over the last 30 days with a ROAS of 3.50.",
                500, 100, new BigDecimal("0.003"), 1200L, null);
        when(claudeClient.sendMessage(anyString(), anyString(), eq("PORTAL_CHAT"),
                eq(AGENCY_ID), eq(CLIENT_ID)))
                .thenReturn(claudeResponse);

        // Act
        ClientPortalAiService.ChatAnswer answer = service.answerQuestion(
                AGENCY_ID, CLIENT_ID, "How are my campaigns performing?");

        // Assert
        assertThat(answer.answer()).contains("$2,500");
        assertThat(answer.tokensUsed()).isEqualTo(600);
        assertThat(answer.cost()).isEqualByComparingTo("0.003");
        verify(claudeClient).sendMessage(anyString(), anyString(), eq("PORTAL_CHAT"),
                eq(AGENCY_ID), eq(CLIENT_ID));
    }

    @Test
    @DisplayName("answerQuestion — Claude failure returns fallback message")
    void answerQuestion_claudeFailure() {
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(null);
        when(campaignRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());
        when(suggestionRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());
        when(reportRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());

        var failResponse = new ClaudeApiClient.ClaudeResponse(
                null, 0, 0, BigDecimal.ZERO, 500L, "API key invalid");
        when(claudeClient.sendMessage(anyString(), anyString(), eq("PORTAL_CHAT"),
                eq(AGENCY_ID), eq(CLIENT_ID)))
                .thenReturn(failResponse);

        ClientPortalAiService.ChatAnswer answer = service.answerQuestion(
                AGENCY_ID, CLIENT_ID, "What's my CTR?");

        assertThat(answer.answer()).contains("unable to answer");
        assertThat(answer.tokensUsed()).isZero();
    }

    // ── buildClientContext ──

    @Test
    @DisplayName("buildClientContext — includes KPIs, campaigns, suggestions, reports")
    void buildClientContext_comprehensive() {
        KpiSummary kpis = new KpiSummary(50_000L, 2_500L,
                new BigDecimal("1200.00"), new BigDecimal("80.00"),
                5.0, new BigDecimal("0.48"), new BigDecimal("2.80"));
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(kpis);

        Campaign camp = new Campaign();
        camp.setName("Spring Sale");
        camp.setStatus("PUBLISHED");
        camp.setObjective("CONVERSIONS");
        when(campaignRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(camp));

        AiSuggestion sugg = new AiSuggestion();
        sugg.setSuggestionType("BUDGET_ADJUST");
        sugg.setRationale("Increase budget for top performer");
        sugg.setStatus("APPLIED");
        when(suggestionRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(sugg));

        Report report;
        try {
            var ctor = Report.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            report = ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        report.setReportType("WEEKLY");
        report.setPeriodStart(LocalDate.now().minusDays(7));
        report.setPeriodEnd(LocalDate.now());
        report.setStatus("SENT");
        report.setCreatedAt(OffsetDateTime.now());
        when(reportRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(report));

        String context = service.buildClientContext(AGENCY_ID, CLIENT_ID);

        assertThat(context).contains("LAST 30 DAYS PERFORMANCE");
        assertThat(context).contains("1200.00");
        assertThat(context).contains("Spring Sale");
        assertThat(context).contains("BUDGET_ADJUST");
        assertThat(context).contains("LAST REPORT: WEEKLY");
    }

    @Test
    @DisplayName("buildClientContext — handles null KPIs gracefully")
    void buildClientContext_nullKpis() {
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(null);
        when(campaignRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());
        when(suggestionRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());
        when(reportRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());

        String context = service.buildClientContext(AGENCY_ID, CLIENT_ID);

        assertThat(context).doesNotContain("LAST 30 DAYS PERFORMANCE");
    }
}
