package com.amp.reports;

import com.amp.insights.KpiSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds professional HTML report content with real KPI data.
 * The HTML is designed to render well both in-browser and when converted to PDF.
 */
public class ReportHtmlBuilder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    /**
     * Build the full HTML report.
     *
     * @param clientName    client display name
     * @param agencyName    agency name (for white-label branding)
     * @param periodStart   report period start
     * @param periodEnd     report period end
     * @param current       KPI summary for current period
     * @param previous      KPI summary for previous period (for comparison)
     * @param dailyData     daily breakdown data (list of maps with date, spend, impressions, clicks, conversions)
     * @param topCampaigns  top campaigns data (list of maps with entityId, spend, impressions, clicks, etc.)
     * @param narrative     optional narrative/commentary from agency (nullable)
     */
    public static String buildHtml(
            String clientName,
            String agencyName,
            LocalDate periodStart,
            LocalDate periodEnd,
            KpiSummary current,
            KpiSummary previous,
            List<Map<String, Object>> dailyData,
            List<Map<String, Object>> topCampaigns,
            String narrative) {

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset='UTF-8'/>");
        html.append("<style>");
        html.append(getStyles());
        html.append("</style>");
        html.append("</head><body>");

        // Header
        html.append("<div class='header'>");
        html.append("<h1>Performance Report</h1>");
        html.append("<div class='subtitle'>").append(escapeHtml(clientName)).append("</div>");
        html.append("<div class='period'>")
            .append(periodStart.format(DATE_FMT))
            .append(" — ")
            .append(periodEnd.format(DATE_FMT))
            .append("</div>");
        html.append("</div>");

        // Executive Summary (narrative)
        if (narrative != null && !narrative.isBlank()) {
            html.append("<div class='section'>");
            html.append("<h2>Executive Summary</h2>");
            html.append("<p>").append(escapeHtml(narrative)).append("</p>");
            html.append("</div>");
        }

        // KPI Cards
        html.append("<div class='section'>");
        html.append("<h2>Key Metrics</h2>");
        html.append("<div class='kpi-grid'>");
        appendKpiCard(html, "Total Spend", formatCurrency(current.getTotalSpend()),
            pctChange(current.getTotalSpend(), previous != null ? previous.getTotalSpend() : null), true);
        appendKpiCard(html, "Impressions", formatNumber(current.getTotalImpressions()),
            pctChangeLong(current.getTotalImpressions(), previous != null ? previous.getTotalImpressions() : null), false);
        appendKpiCard(html, "Clicks", formatNumber(current.getTotalClicks()),
            pctChangeLong(current.getTotalClicks(), previous != null ? previous.getTotalClicks() : null), false);
        appendKpiCard(html, "Conversions", formatDecimal(current.getTotalConversions()),
            pctChange(current.getTotalConversions(), previous != null ? previous.getTotalConversions() : null), false);
        appendKpiCard(html, "CTR", formatPercent(current.getAvgCtr()),
            pctChangeDouble(current.getAvgCtr(), previous != null ? previous.getAvgCtr() : null), false);
        appendKpiCard(html, "CPC", formatCurrency(current.getAvgCpc()),
            pctChange(current.getAvgCpc(), previous != null ? previous.getAvgCpc() : null), true);
        html.append("</div>");
        html.append("</div>");

        // Top Campaigns Table
        if (topCampaigns != null && !topCampaigns.isEmpty()) {
            html.append("<div class='section'>");
            html.append("<h2>Top Campaigns</h2>");
            html.append("<table class='data-table'>");
            html.append("<thead><tr>");
            html.append("<th>Campaign</th><th>Spend</th><th>Impressions</th>");
            html.append("<th>Clicks</th><th>CTR</th><th>Conversions</th>");
            html.append("</tr></thead><tbody>");
            for (Map<String, Object> c : topCampaigns) {
                html.append("<tr>");
                html.append("<td>").append(c.getOrDefault("entityId", "—")).append("</td>");
                html.append("<td>").append(formatCurrency(toBigDecimal(c.get("spend")))).append("</td>");
                html.append("<td>").append(formatNumber(toLong(c.get("impressions")))).append("</td>");
                html.append("<td>").append(formatNumber(toLong(c.get("clicks")))).append("</td>");
                html.append("<td>").append(formatDecimal(toBigDecimal(c.get("ctr")))).append("%</td>");
                html.append("<td>").append(formatDecimal(toBigDecimal(c.get("conversions")))).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
            html.append("</div>");
        }

        // Footer
        html.append("<div class='footer'>");
        html.append("<p>Generated by ").append(escapeHtml(agencyName)).append(" using AI Marketing Platform</p>");
        html.append("<p class='disclaimer'>Data sourced from Meta Ads. Last 1-2 days may contain preliminary data subject to change.</p>");
        html.append("</div>");

        html.append("</body></html>");
        return html.toString();
    }

    private static void appendKpiCard(StringBuilder html, String label, String value, Double change, boolean invertColor) {
        html.append("<div class='kpi-card'>");
        html.append("<div class='kpi-label'>").append(label).append("</div>");
        html.append("<div class='kpi-value'>").append(value).append("</div>");
        if (change != null) {
            boolean isPositive = change > 0;
            // For spend and CPC, positive change is bad
            boolean isGood = invertColor ? !isPositive : isPositive;
            String arrow = isPositive ? "↑" : "↓";
            String color = isGood ? "#4CAF50" : "#F44336";
            html.append("<div class='kpi-change' style='color:").append(color).append("'>");
            html.append(arrow).append(" ").append(String.format(Locale.US, "%.1f", Math.abs(change))).append("%");
            html.append(" vs prev period</div>");
        }
        html.append("</div>");
    }

    // ── Format helpers ──
    static String formatCurrency(BigDecimal val) {
        if (val == null) return "$0.00";
        return String.format(Locale.US, "$%,.2f", val);
    }
    static String formatNumber(Long val) {
        if (val == null) return "0";
        return String.format(Locale.US, "%,d", val);
    }
    static String formatDecimal(BigDecimal val) {
        if (val == null) return "0.00";
        return String.format(Locale.US, "%,.2f", val);
    }
    static String formatPercent(Double val) {
        if (val == null) return "0.00%";
        return String.format(Locale.US, "%.2f%%", val);
    }

    // ── Change calculation ──
    static Double pctChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return null;
        return current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).doubleValue();
    }
    static Double pctChangeLong(Long current, Long previous) {
        if (current == null || previous == null || previous == 0) return null;
        return ((double)(current - previous) / previous) * 100;
    }
    static Double pctChangeDouble(Double current, Double previous) {
        if (current == null || previous == null || previous == 0) return null;
        return ((current - previous) / previous) * 100;
    }

    static BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        return new BigDecimal(val.toString());
    }
    static Long toLong(Object val) {
        if (val == null) return 0L;
        return Long.parseLong(val.toString());
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private static String getStyles() {
        return """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: 'Helvetica Neue', Arial, sans-serif; color: #333; padding: 40px; max-width: 900px; margin: 0 auto; }
            .header { text-align: center; margin-bottom: 40px; padding-bottom: 20px; border-bottom: 2px solid #1976D2; }
            .header h1 { font-size: 28px; color: #1976D2; margin-bottom: 8px; }
            .header .subtitle { font-size: 20px; color: #555; margin-bottom: 4px; }
            .header .period { font-size: 14px; color: #888; }
            .section { margin-bottom: 32px; }
            .section h2 { font-size: 18px; color: #1976D2; margin-bottom: 16px; padding-bottom: 8px; border-bottom: 1px solid #E0E0E0; }
            .kpi-grid { display: flex; flex-wrap: wrap; gap: 16px; }
            .kpi-card { flex: 1; min-width: 130px; background: #F5F5F5; border-radius: 8px; padding: 16px; text-align: center; }
            .kpi-label { font-size: 12px; color: #888; text-transform: uppercase; margin-bottom: 4px; }
            .kpi-value { font-size: 22px; font-weight: bold; color: #333; }
            .kpi-change { font-size: 11px; margin-top: 4px; }
            .data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
            .data-table th { background: #1976D2; color: white; padding: 10px 12px; text-align: left; }
            .data-table td { padding: 8px 12px; border-bottom: 1px solid #EEE; }
            .data-table tr:nth-child(even) { background: #FAFAFA; }
            .footer { margin-top: 40px; padding-top: 16px; border-top: 1px solid #E0E0E0; text-align: center; font-size: 12px; color: #999; }
            .disclaimer { font-size: 10px; color: #BBB; margin-top: 4px; }
            p { line-height: 1.6; margin-bottom: 12px; }
            """;
    }
}
