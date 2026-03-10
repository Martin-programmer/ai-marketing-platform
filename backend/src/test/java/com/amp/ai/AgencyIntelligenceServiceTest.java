package com.amp.ai;

import com.amp.agency.Agency;
import com.amp.agency.AgencyRepository;
import com.amp.campaigns.Campaign;
import com.amp.campaigns.CampaignRepository;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.insights.InsightDailyRepository;
import com.amp.insights.KpiSummary;
import com.amp.meta.MetaConnection;
import com.amp.meta.MetaConnectionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgencyIntelligenceServiceTest {

    private static final UUID AGENCY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Mock private ClaudeApiClient claudeClient;
    @Mock private AiProperties aiProps;
    @Mock private AgencyRepository agencyRepo;
    @Mock private ClientRepository clientRepo;
    @Mock private CampaignRepository campaignRepo;
    @Mock private InsightDailyRepository insightDailyRepo;
    @Mock private AiSuggestionRepository suggestionRepo;
    @Mock private MetaConnectionRepository metaConnectionRepo;

    @InjectMocks
    private AgencyIntelligenceService service;

    // ── Helpers ──

    private Agency buildAgency(UUID id, String name) {
        Agency a = new Agency();
        a.setId(id);
        a.setName(name);
        a.setStatus("ACTIVE");
        a.setPlanCode("PRO");
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());
        return a;
    }

    private Client buildClient(UUID id, UUID agencyId, String name, String industry, String status) {
        try {
            var ctor = Client.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            Client c = ctor.newInstance();
            c.setId(id);
            c.setAgencyId(agencyId);
            c.setName(name);
            c.setIndustry(industry);
            c.setStatus(status);
            return c;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MetaConnection buildConnection(UUID agencyId, UUID clientId,
                                           String status, OffsetDateTime lastSync) {
        try {
            var ctor = MetaConnection.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            MetaConnection mc = ctor.newInstance();
            mc.setId(UUID.randomUUID());
            mc.setAgencyId(agencyId);
            mc.setClientId(clientId);
            mc.setStatus(status);
            mc.setLastSyncAt(lastSync);
            return mc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── computeIndustryBenchmarks ──

    @Test
    @DisplayName("computeIndustryBenchmarks — groups by industry with averages")
    void benchmarks_groupedByIndustry() {
        Client c1 = buildClient(CLIENT_ID, AGENCY_ID, "Client A", "E-Commerce", "ACTIVE");
        UUID c2Id = UUID.randomUUID();
        Client c2 = buildClient(c2Id, AGENCY_ID, "Client B", "E-Commerce", "ACTIVE");

        KpiSummary kpi1 = new KpiSummary(100_000L, 5_000L,
                new BigDecimal("3000.00"), new BigDecimal("200.00"),
                5.0, new BigDecimal("0.60"), new BigDecimal("4.00"));
        KpiSummary kpi2 = new KpiSummary(80_000L, 3_000L,
                new BigDecimal("2000.00"), new BigDecimal("100.00"),
                3.75, new BigDecimal("0.67"), new BigDecimal("2.00"));

        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(kpi1);
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(c2Id), any(), any()))
                .thenReturn(kpi2);

        var benchmarks = service.computeIndustryBenchmarks(
                List.of(c1, c2), java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());

        assertThat(benchmarks).hasSize(1);
        assertThat(benchmarks.get(0).industry()).isEqualTo("E-Commerce");
        assertThat(benchmarks.get(0).clientsWithData()).isEqualTo(2);
        assertThat(benchmarks.get(0).avgCtr()).isEqualTo(4.38); // (5.0+3.75)/2
        assertThat(benchmarks.get(0).totalSpend()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("computeIndustryBenchmarks — skips clients with no industry")
    void benchmarks_skipsNullIndustry() {
        Client c = buildClient(CLIENT_ID, AGENCY_ID, "Client X", null, "ACTIVE");

        var benchmarks = service.computeIndustryBenchmarks(
                List.of(c), java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());

        assertThat(benchmarks).isEmpty();
    }

    // ── computeAgencyHealthScores ──

    @Test
    @DisplayName("computeAgencyHealthScores — calculates health from components")
    void healthScores_computed() {
        Agency agency = buildAgency(AGENCY_ID, "Test Agency");
        Client client = buildClient(CLIENT_ID, AGENCY_ID, "Client A", "SaaS", "ACTIVE");
        MetaConnection conn = buildConnection(AGENCY_ID, CLIENT_ID, "CONNECTED",
                OffsetDateTime.now().minusHours(1));

        KpiSummary kpis = new KpiSummary(100_000L, 5_000L,
                new BigDecimal("3000.00"), new BigDecimal("200.00"),
                5.0, new BigDecimal("0.60"), new BigDecimal("4.00"));
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(kpis);

        AiSuggestion sugg = new AiSuggestion();
        sugg.setStatus("APPLIED");
        when(suggestionRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(sugg));

        var scores = service.computeAgencyHealthScores(
                List.of(agency), List.of(client), List.of(conn),
                java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());

        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).agencyName()).isEqualTo("Test Agency");
        assertThat(scores.get(0).activeClientPct()).isEqualTo(100.0);
        assertThat(scores.get(0).syncFreshnessPct()).isEqualTo(100.0);
        assertThat(scores.get(0).suggestionAdoptionPct()).isEqualTo(100.0);
        assertThat(scores.get(0).healthScore()).isGreaterThan(0);
    }

    @Test
    @DisplayName("computeAgencyHealthScores — handles agency with no clients")
    void healthScores_noClients() {
        Agency agency = buildAgency(AGENCY_ID, "Empty Agency");

        var scores = service.computeAgencyHealthScores(
                List.of(agency), List.of(), List.of(),
                java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());

        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).totalClients()).isZero();
        assertThat(scores.get(0).healthScore()).isEqualTo(0.0);
    }

    // ── computeHealthScore ──

    @Test
    @DisplayName("computeHealthScore — perfect scores yield 100")
    void healthScore_perfect() {
        double score = service.computeHealthScore(100, 5.0, 100, 100);
        assertThat(score).isEqualTo(100.0);
    }

    @Test
    @DisplayName("computeHealthScore — all zeros yield 0")
    void healthScore_zeros() {
        double score = service.computeHealthScore(0, 0, 0, 0);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("computeHealthScore — ROAS caps at 5x for scoring")
    void healthScore_roasCapped() {
        double score1 = service.computeHealthScore(100, 5.0, 100, 100);
        double score2 = service.computeHealthScore(100, 10.0, 100, 100);
        assertThat(score1).isEqualTo(score2); // ROAS capped at 5x
    }

    // ── computeChurnRisks ──

    @Test
    @DisplayName("computeChurnRisks — flags no sync 30+ days")
    void churnRisk_noSync() {
        Client client = buildClient(CLIENT_ID, AGENCY_ID, "Stale Client", "SaaS", "ACTIVE");
        MetaConnection conn = buildConnection(AGENCY_ID, CLIENT_ID, "CONNECTED",
                OffsetDateTime.now().minusDays(45));

        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(null);
        when(campaignRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());

        var risks = service.computeChurnRisks(
                List.of(client), List.of(conn),
                java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());

        assertThat(risks).hasSize(1);
        assertThat(risks.get(0).signals()).anyMatch(s -> s.contains("No sync for"));
    }

    @Test
    @DisplayName("computeChurnRisks — flags poor ROAS below 1.0")
    void churnRisk_poorRoas() {
        Client client = buildClient(CLIENT_ID, AGENCY_ID, "Poor Client", "Retail", "ACTIVE");
        MetaConnection conn = buildConnection(AGENCY_ID, CLIENT_ID, "CONNECTED",
                OffsetDateTime.now().minusHours(1));

        KpiSummary kpis = new KpiSummary(10_000L, 500L,
                new BigDecimal("1000.00"), new BigDecimal("10.00"),
                5.0, new BigDecimal("2.00"), new BigDecimal("0.50"));
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(kpis);
        when(campaignRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());

        var risks = service.computeChurnRisks(
                List.of(client), List.of(conn),
                java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());

        assertThat(risks).hasSize(1);
        assertThat(risks.get(0).signals()).anyMatch(s -> s.contains("ROAS below 1.0"));
    }

    @Test
    @DisplayName("computeChurnRisks — flags all campaigns paused")
    void churnRisk_allPaused() {
        Client client = buildClient(CLIENT_ID, AGENCY_ID, "Paused Client", "Tech", "ACTIVE");
        MetaConnection conn = buildConnection(AGENCY_ID, CLIENT_ID, "CONNECTED",
                OffsetDateTime.now().minusHours(1));

        KpiSummary kpis = new KpiSummary(10_000L, 500L,
                new BigDecimal("500.00"), new BigDecimal("30.00"),
                5.0, new BigDecimal("1.00"), new BigDecimal("2.00"));
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(kpis);

        Campaign camp = new Campaign();
        camp.setStatus("PAUSED");
        when(campaignRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(camp));

        var risks = service.computeChurnRisks(
                List.of(client), List.of(conn),
                java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());

        assertThat(risks).hasSize(1);
        assertThat(risks.get(0).signals()).anyMatch(s -> s.contains("All campaigns paused"));
    }

    @Test
    @DisplayName("computeChurnRisks — multiple signals raise to HIGH")
    void churnRisk_highRisk() {
        Client client = buildClient(CLIENT_ID, AGENCY_ID, "High Risk", "SaaS", "ACTIVE");
        // No connection → "No Meta connection"
        // null KPIs → "Zero spend"
        // All paused campaigns
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(null);

        Campaign camp = new Campaign();
        camp.setStatus("PAUSED");
        when(campaignRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(camp));

        var risks = service.computeChurnRisks(
                List.of(client), List.of(),
                java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());

        assertThat(risks).hasSize(1);
        assertThat(risks.get(0).riskLevel()).isEqualTo("HIGH");
        assertThat(risks.get(0).signals()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("computeChurnRisks — healthy client has no risk signals")
    void churnRisk_healthyClient() {
        Client client = buildClient(CLIENT_ID, AGENCY_ID, "Healthy", "SaaS", "ACTIVE");
        MetaConnection conn = buildConnection(AGENCY_ID, CLIENT_ID, "CONNECTED",
                OffsetDateTime.now().minusHours(1));

        KpiSummary kpis = new KpiSummary(100_000L, 5_000L,
                new BigDecimal("3000.00"), new BigDecimal("200.00"),
                5.0, new BigDecimal("0.60"), new BigDecimal("4.00"));
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(kpis);

        Campaign camp = new Campaign();
        camp.setStatus("PUBLISHED");
        when(campaignRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(camp));

        var risks = service.computeChurnRisks(
                List.of(client), List.of(conn),
                java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());

        assertThat(risks).isEmpty();
    }

    @Test
    @DisplayName("computeChurnRisks — skips non-ACTIVE clients")
    void churnRisk_skipsInactive() {
        Client client = buildClient(CLIENT_ID, AGENCY_ID, "Archived", "SaaS", "ARCHIVED");

        var risks = service.computeChurnRisks(
                List.of(client), List.of(),
                java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());

        assertThat(risks).isEmpty();
    }

    // ── generateNarrative ──

    @Test
    @DisplayName("generateNarrative — returns Claude text on success")
    void narrative_success() {
        var claudeResponse = new ClaudeApiClient.ClaudeResponse(
                "The platform shows strong performance across E-Commerce clients.",
                800, 200, new BigDecimal("0.006"), 2000L, null);
        when(claudeClient.sendMessage(anyString(), anyString(), eq("AGENCY_INTELLIGENCE"),
                isNull(), isNull()))
                .thenReturn(claudeResponse);

        String narrative = service.generateNarrative(List.of(), List.of(), List.of());

        assertThat(narrative).contains("strong performance");
    }

    @Test
    @DisplayName("generateNarrative — returns fallback on Claude failure")
    void narrative_fallback() {
        var failResponse = new ClaudeApiClient.ClaudeResponse(
                null, 0, 0, BigDecimal.ZERO, 500L, "Rate limited");
        when(claudeClient.sendMessage(anyString(), anyString(), eq("AGENCY_INTELLIGENCE"),
                isNull(), isNull()))
                .thenReturn(failResponse);

        String narrative = service.generateNarrative(List.of(), List.of(), List.of());

        assertThat(narrative).contains("unavailable");
    }

    // ── generateIntelligence (integration of all parts) ──

    @Test
    @DisplayName("generateIntelligence — assembles complete report")
    void generateIntelligence_complete() {
        Agency agency = buildAgency(AGENCY_ID, "Test Agency");
        Client client = buildClient(CLIENT_ID, AGENCY_ID, "Client A", "SaaS", "ACTIVE");
        MetaConnection conn = buildConnection(AGENCY_ID, CLIENT_ID, "CONNECTED",
                OffsetDateTime.now().minusHours(1));

        when(agencyRepo.findAll()).thenReturn(List.of(agency));
        when(clientRepo.findAll()).thenReturn(List.of(client));
        when(metaConnectionRepo.findAll()).thenReturn(List.of(conn));

        KpiSummary kpis = new KpiSummary(100_000L, 5_000L,
                new BigDecimal("3000.00"), new BigDecimal("200.00"),
                5.0, new BigDecimal("0.60"), new BigDecimal("4.00"));
        when(insightDailyRepo.aggregateKpis(eq(AGENCY_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(kpis);

        when(suggestionRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of());

        Campaign camp = new Campaign();
        camp.setStatus("PUBLISHED");
        when(campaignRepo.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(camp));

        var claudeResponse = new ClaudeApiClient.ClaudeResponse(
                "Platform looks healthy.", 300, 100, new BigDecimal("0.002"), 1000L, null);
        when(claudeClient.sendMessage(anyString(), anyString(), eq("AGENCY_INTELLIGENCE"),
                isNull(), isNull()))
                .thenReturn(claudeResponse);

        AgencyIntelligenceService.IntelligenceReport report = service.generateIntelligence();

        assertThat(report).isNotNull();
        assertThat(report.benchmarks()).hasSize(1);
        assertThat(report.benchmarks().get(0).industry()).isEqualTo("SaaS");
        assertThat(report.agencyScores()).hasSize(1);
        assertThat(report.agencyScores().get(0).agencyName()).isEqualTo("Test Agency");
        assertThat(report.churnRisks()).isEmpty(); // healthy client
        assertThat(report.aiNarrative()).contains("healthy");
    }
}
