package com.amp.ai;

import com.amp.campaigns.Campaign;
import com.amp.campaigns.CampaignRepository;
import com.amp.insights.InsightDailyRepository;
import com.amp.insights.KpiSummary;
import com.amp.reports.Report;
import com.amp.reports.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI-powered Q&amp;A for CLIENT_USER portal.
 * The client types a question ("How are my campaigns doing?") and gets
 * a data-grounded answer from Claude Sonnet using only their own data.
 */
@Service
public class ClientPortalAiService {

    private static final Logger log = LoggerFactory.getLogger(ClientPortalAiService.class);
    private static final String MODULE = "PORTAL_CHAT";

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;
    private final InsightDailyRepository insightDailyRepo;
    private final CampaignRepository campaignRepo;
    private final AiSuggestionRepository suggestionRepo;
    private final ReportRepository reportRepo;

    public ClientPortalAiService(ClaudeApiClient claudeClient,
                                 AiProperties aiProps,
                                 InsightDailyRepository insightDailyRepo,
                                 CampaignRepository campaignRepo,
                                 AiSuggestionRepository suggestionRepo,
                                 ReportRepository reportRepo) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.insightDailyRepo = insightDailyRepo;
        this.campaignRepo = campaignRepo;
        this.suggestionRepo = suggestionRepo;
        this.reportRepo = reportRepo;
    }

    /**
     * Answer a client's natural language question using their own campaign data.
     *
     * @param agencyId the agency
     * @param clientId the client asking the question
     * @param question the natural language question
     * @return a {@link ChatAnswer} with the AI response and cost info
     */
    public ChatAnswer answerQuestion(UUID agencyId, UUID clientId, String question) {
        log.info("Portal AI chat: agencyId={}, clientId={}, question='{}'", agencyId, clientId, truncate(question, 100));

        // ── 1. Gather context from the client's own data ──
        String context = buildClientContext(agencyId, clientId);

        // ── 2. Build system prompt ──
        String systemPrompt = """
                You are a friendly marketing analytics assistant for a digital advertising client.
                You ONLY answer using the data provided below — never fabricate numbers.
                If the data does not contain enough information to answer, say so honestly.
                Keep answers concise (3-6 sentences) with specific numbers where possible.
                Use a professional but approachable tone.
                Format currency as USD, percentages with one decimal place.

                === CLIENT DATA ===
                %s
                """.formatted(context);

        // ── 3. Call Claude Sonnet ──
        ClaudeApiClient.ClaudeResponse response = claudeClient.sendMessage(
                systemPrompt, question, MODULE, agencyId, clientId);

        if (!response.isSuccess()) {
            log.error("Portal AI chat failed: {}", response.error());
            return new ChatAnswer("Sorry, I'm unable to answer right now. Please try again later.",
                    0, BigDecimal.ZERO);
        }

        log.info("Portal AI chat success: tokens={}, cost=${}", response.inputTokens() + response.outputTokens(), response.cost());
        return new ChatAnswer(response.text(),
                response.inputTokens() + response.outputTokens(),
                response.cost());
    }

    // ── Context builder ──

    String buildClientContext(UUID agencyId, UUID clientId) {
        StringBuilder sb = new StringBuilder();

        // 30-day KPI summary
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);
        KpiSummary kpis = insightDailyRepo.aggregateKpis(agencyId, clientId, thirtyDaysAgo, today);
        if (kpis != null) {
            sb.append("LAST 30 DAYS PERFORMANCE:\n");
            sb.append("  Total Spend: $").append(fmt(kpis.getTotalSpend())).append("\n");
            sb.append("  Total Impressions: ").append(kpis.getTotalImpressions()).append("\n");
            sb.append("  Total Clicks: ").append(kpis.getTotalClicks()).append("\n");
            sb.append("  Total Conversions: ").append(fmt(kpis.getTotalConversions())).append("\n");
            sb.append("  Average CTR: ").append(fmtPct(kpis.getAvgCtr())).append("%\n");
            sb.append("  Average CPC: $").append(fmt(kpis.getAvgCpc())).append("\n");
            sb.append("  ROAS: ").append(fmt(kpis.getAvgRoas())).append("\n\n");
        }

        // Previous 30 days for comparison
        KpiSummary prevKpis = insightDailyRepo.aggregateKpis(agencyId, clientId,
                thirtyDaysAgo.minusDays(30), thirtyDaysAgo.minusDays(1));
        if (prevKpis != null && prevKpis.getTotalSpend() != null
                && prevKpis.getTotalSpend().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("PREVIOUS 30 DAYS (for comparison):\n");
            sb.append("  Total Spend: $").append(fmt(prevKpis.getTotalSpend())).append("\n");
            sb.append("  Total Impressions: ").append(prevKpis.getTotalImpressions()).append("\n");
            sb.append("  Total Clicks: ").append(prevKpis.getTotalClicks()).append("\n");
            sb.append("  Total Conversions: ").append(fmt(prevKpis.getTotalConversions())).append("\n");
            sb.append("  Average CTR: ").append(fmtPct(prevKpis.getAvgCtr())).append("%\n");
            sb.append("  Average CPC: $").append(fmt(prevKpis.getAvgCpc())).append("\n\n");
        }

        // Active campaigns
        List<Campaign> campaigns = campaignRepo.findAllByAgencyIdAndClientId(agencyId, clientId);
        if (!campaigns.isEmpty()) {
            sb.append("CAMPAIGNS (").append(campaigns.size()).append(" total):\n");
            long active = campaigns.stream().filter(c -> "PUBLISHED".equals(c.getStatus())).count();
            long paused = campaigns.stream().filter(c -> "PAUSED".equals(c.getStatus())).count();
            sb.append("  Active: ").append(active).append(", Paused: ").append(paused).append("\n");
            for (Campaign c : campaigns.stream().limit(10).toList()) {
                sb.append("  - ").append(c.getName())
                        .append(" [").append(c.getStatus()).append("] ")
                        .append(c.getObjective() != null ? c.getObjective() : "")
                        .append("\n");
            }
            sb.append("\n");
        }

        // Recent suggestions (last 30 days, only approved/applied)
        List<AiSuggestion> suggestions = suggestionRepo.findAllByAgencyIdAndClientId(agencyId, clientId);
        List<AiSuggestion> recentSuggestions = suggestions.stream()
                .filter(s -> "APPROVED".equals(s.getStatus()) || "APPLIED".equals(s.getStatus()))
                .limit(5)
                .toList();
        if (!recentSuggestions.isEmpty()) {
            sb.append("RECENT AI SUGGESTIONS (approved/applied):\n");
            for (AiSuggestion s : recentSuggestions) {
                sb.append("  - [").append(s.getSuggestionType()).append("] ").append(s.getRationale()).append("\n");
            }
            sb.append("\n");
        }

        // Most recent report summary
        List<Report> reports = reportRepo.findAllByAgencyIdAndClientId(agencyId, clientId);
        reports.stream()
                .filter(r -> "SENT".equals(r.getStatus()) || "APPROVED".equals(r.getStatus()))
                .reduce((a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b)
                .ifPresent(r -> {
                    sb.append("LAST REPORT: ").append(r.getReportType())
                            .append(" covering ").append(r.getPeriodStart())
                            .append(" to ").append(r.getPeriodEnd()).append("\n\n");
                });

        return sb.toString();
    }

    // ── Helpers ──

    private static String fmt(BigDecimal val) {
        return val != null ? val.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "0.00";
    }

    private static String fmtPct(Double val) {
        return val != null ? String.format("%.1f", val) : "0.0";
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }

    // ── Response record ──

    public record ChatAnswer(String answer, int tokensUsed, BigDecimal cost) {}
}
