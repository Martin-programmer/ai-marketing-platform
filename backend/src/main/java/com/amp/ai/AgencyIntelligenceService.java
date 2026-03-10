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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Platform-wide intelligence for OWNER_ADMIN.
 * Computes benchmarks by industry, agency health scores, and churn risk signals,
 * then asks Claude for a narrative summary.
 */
@Service
public class AgencyIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(AgencyIntelligenceService.class);
    private static final String MODULE = "AGENCY_INTELLIGENCE";

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;
    private final AgencyRepository agencyRepo;
    private final ClientRepository clientRepo;
    private final CampaignRepository campaignRepo;
    private final InsightDailyRepository insightDailyRepo;
    private final AiSuggestionRepository suggestionRepo;
    private final MetaConnectionRepository metaConnectionRepo;

    public AgencyIntelligenceService(ClaudeApiClient claudeClient,
                                     AiProperties aiProps,
                                     AgencyRepository agencyRepo,
                                     ClientRepository clientRepo,
                                     CampaignRepository campaignRepo,
                                     InsightDailyRepository insightDailyRepo,
                                     AiSuggestionRepository suggestionRepo,
                                     MetaConnectionRepository metaConnectionRepo) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.agencyRepo = agencyRepo;
        this.clientRepo = clientRepo;
        this.campaignRepo = campaignRepo;
        this.insightDailyRepo = insightDailyRepo;
        this.suggestionRepo = suggestionRepo;
        this.metaConnectionRepo = metaConnectionRepo;
    }

    /**
     * Generate platform-wide intelligence report.
     *
     * @return an {@link IntelligenceReport} with benchmarks, agency health scores,
     *         churn risk signals, and an AI narrative
     */
    public IntelligenceReport generateIntelligence() {
        log.info("Generating agency intelligence report");

        List<Agency> agencies = agencyRepo.findAll();
        List<Client> allClients = clientRepo.findAll();
        List<MetaConnection> connections = metaConnectionRepo.findAll();

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);

        // ── 1. Industry benchmarks ──
        List<IndustryBenchmark> benchmarks = computeIndustryBenchmarks(allClients, thirtyDaysAgo, today);

        // ── 2. Agency health scores ──
        List<AgencyHealthScore> healthScores = computeAgencyHealthScores(
                agencies, allClients, connections, thirtyDaysAgo, today);

        // ── 3. Churn risk signals ──
        List<ChurnRisk> churnRisks = computeChurnRisks(allClients, connections, thirtyDaysAgo, today);

        // ── 4. AI narrative ──
        String narrative = generateNarrative(benchmarks, healthScores, churnRisks);

        log.info("Intelligence report complete: {} benchmarks, {} agency scores, {} churn risks",
                benchmarks.size(), healthScores.size(), churnRisks.size());

        return new IntelligenceReport(benchmarks, healthScores, churnRisks, narrative);
    }

    // ── Industry benchmarks ──

    List<IndustryBenchmark> computeIndustryBenchmarks(List<Client> clients,
                                                              LocalDate from, LocalDate to) {
        Map<String, List<Client>> byIndustry = clients.stream()
                .filter(c -> c.getIndustry() != null && !c.getIndustry().isBlank())
                .collect(Collectors.groupingBy(Client::getIndustry));

        List<IndustryBenchmark> benchmarks = new ArrayList<>();
        for (Map.Entry<String, List<Client>> entry : byIndustry.entrySet()) {
            String industry = entry.getKey();
            List<Client> group = entry.getValue();

            double totalCtr = 0;
            BigDecimal totalCpc = BigDecimal.ZERO;
            BigDecimal totalRoas = BigDecimal.ZERO;
            BigDecimal totalSpend = BigDecimal.ZERO;
            int clientsWithData = 0;

            for (Client c : group) {
                KpiSummary kpis = insightDailyRepo.aggregateKpis(c.getAgencyId(), c.getId(), from, to);
                if (kpis != null && kpis.getTotalSpend() != null
                        && kpis.getTotalSpend().compareTo(BigDecimal.ZERO) > 0) {
                    clientsWithData++;
                    totalCtr += kpis.getAvgCtr() != null ? kpis.getAvgCtr() : 0;
                    totalCpc = totalCpc.add(kpis.getAvgCpc() != null ? kpis.getAvgCpc() : BigDecimal.ZERO);
                    totalRoas = totalRoas.add(kpis.getAvgRoas() != null ? kpis.getAvgRoas() : BigDecimal.ZERO);
                    totalSpend = totalSpend.add(kpis.getTotalSpend());
                }
            }

            if (clientsWithData > 0) {
                benchmarks.add(new IndustryBenchmark(
                        industry,
                        group.size(),
                        clientsWithData,
                        round(totalCtr / clientsWithData, 2),
                        totalCpc.divide(BigDecimal.valueOf(clientsWithData), 2, RoundingMode.HALF_UP),
                        totalRoas.divide(BigDecimal.valueOf(clientsWithData), 2, RoundingMode.HALF_UP),
                        totalSpend
                ));
            }
        }

        benchmarks.sort(Comparator.comparing(IndustryBenchmark::totalSpend).reversed());
        return benchmarks;
    }

    // ── Agency health scores ──

    List<AgencyHealthScore> computeAgencyHealthScores(List<Agency> agencies,
                                                              List<Client> allClients,
                                                              List<MetaConnection> connections,
                                                              LocalDate from, LocalDate to) {
        Map<UUID, List<Client>> clientsByAgency = allClients.stream()
                .collect(Collectors.groupingBy(Client::getAgencyId));
        Map<UUID, List<MetaConnection>> connsByAgency = connections.stream()
                .collect(Collectors.groupingBy(MetaConnection::getAgencyId));

        List<AgencyHealthScore> scores = new ArrayList<>();
        for (Agency agency : agencies) {
            List<Client> agClients = clientsByAgency.getOrDefault(agency.getId(), List.of());
            List<MetaConnection> agConns = connsByAgency.getOrDefault(agency.getId(), List.of());

            int totalClients = agClients.size();
            long activeClients = agClients.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
            double activeClientPct = totalClients > 0 ? round(100.0 * activeClients / totalClients, 1) : 0;

            // Average ROAS across this agency's clients
            BigDecimal sumRoas = BigDecimal.ZERO;
            int roasCount = 0;
            for (Client c : agClients) {
                KpiSummary kpis = insightDailyRepo.aggregateKpis(agency.getId(), c.getId(), from, to);
                if (kpis != null && kpis.getAvgRoas() != null) {
                    sumRoas = sumRoas.add(kpis.getAvgRoas());
                    roasCount++;
                }
            }
            BigDecimal avgRoas = roasCount > 0
                    ? sumRoas.divide(BigDecimal.valueOf(roasCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Sync freshness: % of connections synced in last 48h
            long freshSyncs = agConns.stream()
                    .filter(c -> "CONNECTED".equals(c.getStatus())
                            && c.getLastSyncAt() != null
                            && c.getLastSyncAt().isAfter(OffsetDateTime.now().minusHours(48)))
                    .count();
            long totalConns = agConns.stream().filter(c -> "CONNECTED".equals(c.getStatus())).count();
            double syncFreshnessPct = totalConns > 0 ? round(100.0 * freshSyncs / totalConns, 1) : 0;

            // Suggestion adoption: approved+applied / total
            int totalSuggs = 0;
            int adoptedSuggs = 0;
            for (Client c : agClients) {
                List<AiSuggestion> suggs = suggestionRepo.findAllByAgencyIdAndClientId(agency.getId(), c.getId());
                totalSuggs += suggs.size();
                adoptedSuggs += (int) suggs.stream()
                        .filter(s -> "APPROVED".equals(s.getStatus()) || "APPLIED".equals(s.getStatus()))
                        .count();
            }
            double adoptionPct = totalSuggs > 0 ? round(100.0 * adoptedSuggs / totalSuggs, 1) : 0;

            // Overall health score (0-100)
            double health = computeHealthScore(activeClientPct, avgRoas.doubleValue(),
                    syncFreshnessPct, adoptionPct);

            scores.add(new AgencyHealthScore(
                    agency.getId(), agency.getName(), agency.getStatus(),
                    totalClients, activeClientPct, avgRoas, syncFreshnessPct,
                    adoptionPct, round(health, 0)
            ));
        }

        scores.sort(Comparator.comparingDouble(AgencyHealthScore::healthScore).reversed());
        return scores;
    }

    double computeHealthScore(double activeClientPct, double avgRoas,
                                      double syncFreshnessPct, double adoptionPct) {
        // Weighted: 25% active clients, 25% ROAS (capped 5x = 100%), 25% sync freshness, 25% adoption
        double roasScore = Math.min(avgRoas / 5.0 * 100.0, 100.0);
        return 0.25 * activeClientPct + 0.25 * roasScore + 0.25 * syncFreshnessPct + 0.25 * adoptionPct;
    }

    // ── Churn risk ──

    List<ChurnRisk> computeChurnRisks(List<Client> clients,
                                              List<MetaConnection> connections,
                                              LocalDate from, LocalDate to) {
        Map<UUID, MetaConnection> connByClient = connections.stream()
                .collect(Collectors.toMap(MetaConnection::getClientId, c -> c, (a, b) -> a));

        List<ChurnRisk> risks = new ArrayList<>();
        for (Client client : clients) {
            if (!"ACTIVE".equals(client.getStatus())) continue;

            List<String> signals = new ArrayList<>();

            // Signal 1: No sync in 30+ days
            MetaConnection conn = connByClient.get(client.getId());
            if (conn == null) {
                signals.add("No Meta connection configured");
            } else if (conn.getLastSyncAt() == null) {
                signals.add("Never synced");
            } else if (conn.getLastSyncAt().isBefore(OffsetDateTime.now().minusDays(30))) {
                long daysSince = ChronoUnit.DAYS.between(
                        conn.getLastSyncAt().toLocalDate(), LocalDate.now());
                signals.add("No sync for " + daysSince + " days");
            }

            // Signal 2: Poor ROAS (below 1.0)
            KpiSummary kpis = insightDailyRepo.aggregateKpis(client.getAgencyId(), client.getId(), from, to);
            if (kpis != null && kpis.getAvgRoas() != null
                    && kpis.getTotalSpend() != null
                    && kpis.getTotalSpend().compareTo(BigDecimal.ZERO) > 0
                    && kpis.getAvgRoas().compareTo(BigDecimal.ONE) < 0) {
                signals.add("ROAS below 1.0 (currently " + kpis.getAvgRoas().setScale(2, RoundingMode.HALF_UP) + ")");
            }

            // Signal 3: No spend in last 30 days
            if (kpis == null || kpis.getTotalSpend() == null
                    || kpis.getTotalSpend().compareTo(BigDecimal.ZERO) == 0) {
                signals.add("Zero spend in last 30 days");
            }

            // Signal 4: All campaigns paused
            List<Campaign> campaigns = campaignRepo.findAllByAgencyIdAndClientId(client.getAgencyId(), client.getId());
            if (!campaigns.isEmpty()
                    && campaigns.stream().allMatch(c -> "PAUSED".equals(c.getStatus()))) {
                signals.add("All campaigns paused");
            }

            if (!signals.isEmpty()) {
                String riskLevel = signals.size() >= 3 ? "HIGH"
                        : signals.size() >= 2 ? "MEDIUM" : "LOW";
                risks.add(new ChurnRisk(
                        client.getId(), client.getAgencyId(),
                        client.getName(), client.getIndustry(),
                        riskLevel, signals
                ));
            }
        }

        risks.sort(Comparator.comparingInt((ChurnRisk r) -> riskOrder(r.riskLevel())).reversed());
        return risks;
    }

    private static int riskOrder(String level) {
        return switch (level) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    // ── AI narrative ──

    String generateNarrative(List<IndustryBenchmark> benchmarks,
                                     List<AgencyHealthScore> healthScores,
                                     List<ChurnRisk> churnRisks) {
        StringBuilder context = new StringBuilder();

        context.append("INDUSTRY BENCHMARKS (last 30 days):\n");
        for (IndustryBenchmark b : benchmarks) {
            context.append("  ").append(b.industry())
                    .append(": ").append(b.clientsWithData()).append(" clients with data")
                    .append(", avg CTR=").append(b.avgCtr()).append("%")
                    .append(", avg CPC=$").append(b.avgCpc())
                    .append(", avg ROAS=").append(b.avgRoas())
                    .append(", total spend=$").append(b.totalSpend())
                    .append("\n");
        }

        context.append("\nAGENCY HEALTH SCORES:\n");
        for (AgencyHealthScore s : healthScores) {
            context.append("  ").append(s.agencyName())
                    .append(": score=").append(s.healthScore())
                    .append(", clients=").append(s.totalClients())
                    .append(", active=").append(s.activeClientPct()).append("%")
                    .append(", avgROAS=").append(s.avgRoas())
                    .append(", syncFresh=").append(s.syncFreshnessPct()).append("%")
                    .append(", adoption=").append(s.suggestionAdoptionPct()).append("%")
                    .append("\n");
        }

        long highChurn = churnRisks.stream().filter(r -> "HIGH".equals(r.riskLevel())).count();
        long medChurn = churnRisks.stream().filter(r -> "MEDIUM".equals(r.riskLevel())).count();
        context.append("\nCHURN RISK SUMMARY: ")
                .append(churnRisks.size()).append(" clients at risk (")
                .append(highChurn).append(" HIGH, ")
                .append(medChurn).append(" MEDIUM)\n");
        for (ChurnRisk r : churnRisks.stream().limit(10).toList()) {
            context.append("  ").append(r.clientName())
                    .append(" [").append(r.riskLevel()).append("]: ")
                    .append(String.join("; ", r.signals())).append("\n");
        }

        String systemPrompt = """
                You are a strategic advisor to a platform owner who manages multiple advertising agencies.
                Analyze the intelligence data below and provide a concise executive briefing (4-8 sentences).
                Highlight: top-performing industries, agencies needing attention, urgent churn risks.
                Be specific with numbers. Use a professional, actionable tone.

                === INTELLIGENCE DATA ===
                %s
                """.formatted(context.toString());

        ClaudeApiClient.ClaudeResponse response = claudeClient.sendMessage(
                systemPrompt, "Provide an executive intelligence briefing.",
                MODULE, null, null);

        if (response.isSuccess()) {
            return response.text();
        }
        log.warn("AI narrative generation failed: {}", response.error());
        return "Intelligence narrative unavailable. Review the data above for insights.";
    }

    // ── Helpers ──

    private static double round(double val, int places) {
        return BigDecimal.valueOf(val).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    // ── Response records ──

    public record IntelligenceReport(
            List<IndustryBenchmark> benchmarks,
            List<AgencyHealthScore> agencyScores,
            List<ChurnRisk> churnRisks,
            String aiNarrative
    ) {}

    public record IndustryBenchmark(
            String industry,
            int totalClients,
            int clientsWithData,
            double avgCtr,
            BigDecimal avgCpc,
            BigDecimal avgRoas,
            BigDecimal totalSpend
    ) {}

    public record AgencyHealthScore(
            UUID agencyId,
            String agencyName,
            String status,
            int totalClients,
            double activeClientPct,
            BigDecimal avgRoas,
            double syncFreshnessPct,
            double suggestionAdoptionPct,
            double healthScore
    ) {}

    public record ChurnRisk(
            UUID clientId,
            UUID agencyId,
            String clientName,
            String industry,
            String riskLevel,
            List<String> signals
    ) {}
}
