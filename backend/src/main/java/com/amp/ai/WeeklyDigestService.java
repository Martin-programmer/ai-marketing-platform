package com.amp.ai;

import com.amp.ai.ClaudeApiClient.ClaudeResponse;
import com.amp.auth.UserAccountRepository;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.common.NotificationHelper;
import com.amp.insights.InsightDailyRepository;
import com.amp.insights.KpiSummary;
import com.amp.meta.MetaConnection;
import com.amp.meta.MetaConnectionRepository;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Generates weekly performance digest emails for clients.
 * Scheduled every Monday at 09:00. For now saves digest to DB — actual email sending is V2.
 */
@Service
public class WeeklyDigestService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestService.class);
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;
    private final MetaConnectionRepository metaConnectionRepo;
    private final ClientRepository clientRepo;
    private final UserAccountRepository userAccountRepo;
    private final InsightDailyRepository insightRepo;
    private final AiWeeklyDigestRepository digestRepo;
    private final NotificationHelper notificationHelper;
    private final AiContextBuilder aiContextBuilder;
    private final AiCrossModuleSupportService aiCrossModuleSupportService;

    public WeeklyDigestService(ClaudeApiClient claudeClient,
                                AiProperties aiProps,
                                MetaConnectionRepository metaConnectionRepo,
                                ClientRepository clientRepo,
                                UserAccountRepository userAccountRepo,
                                InsightDailyRepository insightRepo,
                                AiWeeklyDigestRepository digestRepo,
                                NotificationHelper notificationHelper,
                                AiContextBuilder aiContextBuilder,
                                AiCrossModuleSupportService aiCrossModuleSupportService) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.metaConnectionRepo = metaConnectionRepo;
        this.clientRepo = clientRepo;
        this.userAccountRepo = userAccountRepo;
        this.insightRepo = insightRepo;
        this.digestRepo = digestRepo;
        this.notificationHelper = notificationHelper;
        this.aiContextBuilder = aiContextBuilder;
        this.aiCrossModuleSupportService = aiCrossModuleSupportService;
    }

    // ──────── Scheduled trigger ────────

    @Scheduled(cron = "0 0 9 * * MON")
    public void scheduledGenerate() {
        log.info("Weekly Digest: scheduled run started");
        generateAndSendDigests();
    }

    // ──────── Core logic ────────

    /**
     * Generate digests for all CONNECTED clients that have at least one user.
     * Returns a summary map with counts.
     */
    public Map<String, Object> generateAndSendDigests() {
        List<MetaConnection> connections = metaConnectionRepo.findByStatus("CONNECTED");
        int processed = 0, generated = 0, skipped = 0, failed = 0;

        for (MetaConnection conn : connections) {
            processed++;
            try {
                TenantContextHolder.set(systemTenantContext(conn.getAgencyId(), conn.getClientId()));

                // Only generate for clients that have at least one user account
                if (!userAccountRepo.existsByClientId(conn.getClientId())) {
                    log.debug("Weekly Digest: skipping client {} — no users", conn.getClientId());
                    skipped++;
                    continue;
                }

                AiWeeklyDigest digest = generateDigest(conn.getAgencyId(), conn.getClientId());
                if (digest != null) {
                    generated++;
                    log.info("Weekly Digest: generated for client {} (id={})",
                            conn.getClientId(), digest.getId());
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Weekly Digest: failed for client {}: {}",
                        conn.getClientId(), e.getMessage(), e);
            } finally {
                TenantContextHolder.clear();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processed", processed);
        result.put("generated", generated);
        result.put("skipped", skipped);
        result.put("failed", failed);

        log.info("Weekly Digest: completed — processed={}, generated={}, skipped={}, failed={}",
                processed, generated, skipped, failed);
        return result;
    }

    private TenantContext systemTenantContext(UUID agencyId, UUID clientId) {
        return new TenantContext(agencyId, SYSTEM_USER_ID, "system@scheduled-job.local", "SYSTEM", clientId);
    }

    /**
     * Generate a single digest for a specific client.
     */
    public AiWeeklyDigest generateDigest(UUID agencyId, UUID clientId) {
        // Period: last 7 days
        LocalDate periodEnd = LocalDate.now().minusDays(1);    // yesterday
        LocalDate periodStart = periodEnd.minusDays(6);         // 7 days total

        // Previous period for comparison
        LocalDate prevEnd = periodStart.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(6);

        // Client name
        String clientName = clientRepo.findByIdAndAgencyId(clientId, agencyId)
                .map(Client::getName).orElse("Client");

        // Aggregate KPIs
        KpiSummary current = insightRepo.aggregateKpis(agencyId, clientId, periodStart, periodEnd);
        KpiSummary previous = insightRepo.aggregateKpis(agencyId, clientId, prevStart, prevEnd);

        if (current == null || (current.getTotalImpressions() == null || current.getTotalImpressions() == 0)) {
            log.debug("Weekly Digest: no data for client {} in period {} — {}", clientId, periodStart, periodEnd);
            return null;
        }

        CompletableFuture<String> sharedContextFuture = CompletableFuture.supplyAsync(
            () -> aiContextBuilder.buildContext(agencyId, clientId));
        CompletableFuture<String> aiActivityFuture = aiCrossModuleSupportService.buildAiActivitySummaryAsync(
            agencyId, clientId, periodStart, periodEnd, false, "week");

        // Build context for Claude
        StringBuilder ctx = new StringBuilder();
        ctx.append("CLIENT: ").append(clientName).append("\n");
        ctx.append("PERIOD: ").append(periodStart).append(" to ").append(periodEnd).append(" (last 7 days)\n\n");

        ctx.append("=== THIS WEEK ===\n");
        appendKpis(ctx, current);

        ctx.append("=== PREVIOUS WEEK ===\n");
        if (previous != null && previous.getTotalImpressions() != null && previous.getTotalImpressions() > 0) {
            appendKpis(ctx, previous);
        } else {
            ctx.append("No data for previous week.\n\n");
        }

        // Changes
        ctx.append("=== WEEK-OVER-WEEK CHANGES ===\n");
        appendChange(ctx, "Spend", current.getTotalSpend(), previous != null ? previous.getTotalSpend() : null);
        appendChangeLong(ctx, "Impressions", current.getTotalImpressions(),
                previous != null ? previous.getTotalImpressions() : null);
        appendChangeLong(ctx, "Clicks", current.getTotalClicks(),
                previous != null ? previous.getTotalClicks() : null);
        appendChange(ctx, "Conversions", current.getTotalConversions(),
                previous != null ? previous.getTotalConversions() : null);
        String aiActivitySummary = aiActivityFuture.join();
        if (aiActivitySummary != null && !aiActivitySummary.isBlank()) {
            ctx.append("\n=== AI ACTIVITY THIS WEEK ===\n")
                .append(aiActivitySummary)
                .append("\n");
        }
        ctx.append("\n=== SHARED CLIENT CONTEXT ===\n")
            .append(sharedContextFuture.join())
            .append("\n");

        // Call Claude (Sonnet — fast and cheap)
        String systemPrompt = """
                You are a friendly marketing performance assistant writing a weekly summary email.
                Write a brief weekly performance summary (5-8 sentences).
                Include: key metrics with actual numbers, highlight of the week, what was optimized.
                Use a friendly professional tone — like a trusted advisor sharing results.
                
                Respond with STRICT JSON only, no markdown:
                {
                  "subjectLine": "Your Weekly Performance Summary — [key highlight]",
                  "greeting": "Hi [client name] team,",
                  "body": "... the 5-8 sentence summary ...",
                  "signoff": "Best regards,\\nYour AI Marketing Platform"
                }
                """;

        ClaudeResponse response = claudeClient.sendMessage(
                systemPrompt, ctx.toString(),
                "WEEKLY_DIGEST", agencyId, clientId);

        if (!response.isSuccess()) {
            log.warn("Weekly Digest: Claude call failed for client {}: {}", clientId, response.error());
            return null;
        }

        JsonNode json = claudeClient.parseJson(response.text());
        if (json == null) {
            log.warn("Weekly Digest: Failed to parse Claude response for client {}", clientId);
            return null;
        }

        String subjectLine = json.has("subjectLine") ? json.get("subjectLine").asText() : "Weekly Performance Summary";
        String greeting = json.has("greeting") ? json.get("greeting").asText() : "Hi,";
        String body = json.has("body") ? json.get("body").asText() : "";
        String signoff = json.has("signoff") ? json.get("signoff").asText() : "Best regards,\nAI Marketing Platform";

        // Build HTML email
        String htmlContent = buildDigestHtml(clientName, periodStart, periodEnd,
                current, previous, greeting, body, signoff);

        // Persist
        AiWeeklyDigest digest = new AiWeeklyDigest();
        digest.setAgencyId(agencyId);
        digest.setClientId(clientId);
        digest.setPeriodStart(periodStart);
        digest.setPeriodEnd(periodEnd);
        digest.setSubjectLine(subjectLine);
        digest.setHtmlContent(htmlContent);
        digest.setCreatedAt(OffsetDateTime.now());

        AiWeeklyDigest saved = digestRepo.save(digest);

        // Send digest email to CLIENT_USERs
        try {
            List<String> recipients = notificationHelper.getClientUserEmails(clientId);
            for (String email : recipients) {
                notificationHelper.sendRawAsync(email, subjectLine, htmlContent);
            }
            saved.setSentAt(OffsetDateTime.now());
            digestRepo.save(saved);
            log.info("Weekly Digest: sent to {} CLIENT_USER(s) for client {} (id={})",
                    recipients.size(), clientId, saved.getId());
        } catch (Exception e) {
            log.warn("Weekly Digest: failed to send emails for client {}: {}",
                    clientId, e.getMessage());
        }

        return saved;
    }

    // ──────── HTML builder ────────

    private String buildDigestHtml(String clientName,
                                    LocalDate periodStart, LocalDate periodEnd,
                                    KpiSummary current, KpiSummary previous,
                                    String greeting, String body, String signoff) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>");
        html.append("""
                body { font-family: 'Helvetica Neue', Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background: #1976D2; color: white; padding: 24px; text-align: center; border-radius: 8px 8px 0 0; }
                .header h1 { margin: 0; font-size: 22px; }
                .header .period { font-size: 13px; opacity: 0.85; margin-top: 4px; }
                .content { background: #FFFFFF; padding: 24px; border: 1px solid #E0E0E0; }
                .greeting { font-size: 15px; margin-bottom: 16px; }
                .body-text { font-size: 14px; line-height: 1.6; margin-bottom: 20px; }
                .kpi-row { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
                .kpi-box { flex: 1; min-width: 120px; background: #F5F5F5; border-radius: 6px; padding: 12px; text-align: center; }
                .kpi-box .label { font-size: 11px; color: #888; text-transform: uppercase; }
                .kpi-box .value { font-size: 20px; font-weight: bold; margin: 4px 0; }
                .kpi-box .change { font-size: 11px; }
                .positive { color: #4CAF50; }
                .negative { color: #F44336; }
                .signoff { font-size: 14px; color: #666; margin-top: 16px; white-space: pre-line; }
                .footer { background: #F9F9F9; padding: 16px; text-align: center; font-size: 11px; color: #999; border-radius: 0 0 8px 8px; border: 1px solid #E0E0E0; border-top: 0; }
                """);
        html.append("</style></head><body>");

        // Header
        html.append("<div class='header'>");
        html.append("<h1>Weekly Performance Summary</h1>");
        html.append("<div class='period'>").append(clientName).append(" — ")
                .append(periodStart).append(" to ").append(periodEnd).append("</div>");
        html.append("</div>");

        // Content
        html.append("<div class='content'>");

        // Greeting
        html.append("<div class='greeting'>").append(escapeHtml(greeting)).append("</div>");

        // Body text
        html.append("<div class='body-text'>").append(escapeHtml(body).replace("\n", "<br>")).append("</div>");

        // KPI snapshot
        html.append("<div class='kpi-row'>");
        appendDigestKpi(html, "Spend", "$" + fmt(current.getTotalSpend()),
                pctChange(current.getTotalSpend(), previous != null ? previous.getTotalSpend() : null), true);
        appendDigestKpi(html, "Clicks", String.valueOf(current.getTotalClicks()),
                pctChangeLong(current.getTotalClicks(), previous != null ? previous.getTotalClicks() : null), false);
        appendDigestKpi(html, "Conversions", fmt(current.getTotalConversions()),
                pctChange(current.getTotalConversions(), previous != null ? previous.getTotalConversions() : null), false);
        appendDigestKpi(html, "ROAS", fmt(current.getAvgRoas()),
                pctChange(current.getAvgRoas(), previous != null ? previous.getAvgRoas() : null), false);
        html.append("</div>");

        // Signoff
        html.append("<div class='signoff'>").append(escapeHtml(signoff).replace("\n", "<br>")).append("</div>");

        html.append("</div>");

        // Footer
        html.append("<div class='footer'>");
        html.append("Powered by AI Marketing Platform<br>");
        html.append("This is an automated weekly summary. Email sending will be enabled in a future update.");
        html.append("</div>");

        html.append("</body></html>");
        return html.toString();
    }

    private void appendDigestKpi(StringBuilder html, String label, String value,
                                  Double change, boolean invertColor) {
        html.append("<div class='kpi-box'>");
        html.append("<div class='label'>").append(label).append("</div>");
        html.append("<div class='value'>").append(value).append("</div>");
        if (change != null) {
            boolean isPositive = change > 0;
            boolean isGood = invertColor ? !isPositive : isPositive;
            String css = isGood ? "positive" : "negative";
            String arrow = isPositive ? "↑" : "↓";
            html.append("<div class='change ").append(css).append("'>");
            html.append(arrow).append(" ").append(String.format(Locale.US, "%.1f", Math.abs(change))).append("%");
            html.append("</div>");
        }
        html.append("</div>");
    }

    // ──────── Helpers ────────

    private void appendKpis(StringBuilder sb, KpiSummary k) {
        sb.append("Spend: $").append(fmt(k.getTotalSpend())).append("\n");
        sb.append("Impressions: ").append(k.getTotalImpressions()).append("\n");
        sb.append("Clicks: ").append(k.getTotalClicks()).append("\n");
        sb.append("Conversions: ").append(fmt(k.getTotalConversions())).append("\n");
        sb.append("CTR: ").append(String.format("%.2f",
                k.getAvgCtr() != null ? k.getAvgCtr() : 0.0)).append("%\n");
        sb.append("CPC: $").append(fmt(k.getAvgCpc())).append("\n");
        sb.append("ROAS: ").append(fmt(k.getAvgRoas())).append("\n\n");
    }

    private void appendChange(StringBuilder sb, String label, BigDecimal current, BigDecimal previous) {
        Double pct = pctChange(current, previous);
        sb.append(label).append(": ");
        if (pct != null) {
            sb.append(String.format(Locale.US, "%+.1f%%", pct));
        } else {
            sb.append("N/A");
        }
        sb.append("\n");
    }

    private void appendChangeLong(StringBuilder sb, String label, Long current, Long previous) {
        Double pct = pctChangeLong(current, previous);
        sb.append(label).append(": ");
        if (pct != null) {
            sb.append(String.format(Locale.US, "%+.1f%%", pct));
        } else {
            sb.append("N/A");
        }
        sb.append("\n");
    }

    private String fmt(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    static Double pctChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return null;
        return current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    static Double pctChangeLong(Long current, Long previous) {
        if (current == null || previous == null || previous == 0) return null;
        return ((double) (current - previous) / previous) * 100;
    }
}
