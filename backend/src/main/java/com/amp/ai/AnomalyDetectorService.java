package com.amp.ai;

import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.common.EmailProperties;
import com.amp.common.NotificationHelper;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure-statistical anomaly detector — no LLM calls.
 * Compares yesterday's/today's data against a 14-day baseline.
 * Creates DIAGNOSTIC {@link AiSuggestion} records for detected anomalies.
 * <p>
 * Designed to run automatically after every Meta sync job and optionally
 * via a manual endpoint.
 */
@Service
public class AnomalyDetectorService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectorService.class);

    // Thresholds (compile-time constants — could move to AiProperties later)
    private static final double SPEND_SPIKE_FACTOR = 2.0;          // daily spend > 2× avg
    private static final double CONVERSION_DROP_MIN_AVG = 5.0;     // avg must be ≥5/day
    private static final double CPM_SURGE_FACTOR = 1.5;            // CPM > 1.5× avg
    private static final double CTR_COLLAPSE_FACTOR = 0.50;        // CTR dropped > 50%
    private static final int BASELINE_DAYS = 14;
    private static final int CPM_SURGE_LOOKBACK_DAYS = 3;

    private final AiSuggestionRepository suggestionRepo;
    private final InsightDailyRepository insightRepo;
    private final ObjectMapper objectMapper;
    private final NotificationHelper notificationHelper;
    private final EmailProperties emailProperties;
    private final ClientRepository clientRepo;

    public AnomalyDetectorService(AiSuggestionRepository suggestionRepo,
                                   InsightDailyRepository insightRepo,
                                   ObjectMapper objectMapper,
                                   NotificationHelper notificationHelper,
                                   EmailProperties emailProperties,
                                   ClientRepository clientRepo) {
        this.suggestionRepo = suggestionRepo;
        this.insightRepo = insightRepo;
        this.objectMapper = objectMapper;
        this.notificationHelper = notificationHelper;
        this.emailProperties = emailProperties;
        this.clientRepo = clientRepo;
    }

    // ══════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════

    /**
     * Detect anomalies for a given client. Intended to be called after every sync
     * and from a manual endpoint.
     *
     * @return summary map with anomaliesDetected + detail list
     */
    @Transactional
    public Map<String, Object> detectAnomalies(UUID agencyId, UUID clientId) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate baselineStart = today.minusDays(BASELINE_DAYS + 1); // 15 days back to get full 14-day window

        List<InsightDaily> allInsights = insightRepo.findAllByAgencyIdAndClientIdAndDateBetween(
                agencyId, clientId, baselineStart, today);

        if (allInsights.isEmpty()) {
            return Map.of("anomaliesDetected", 0, "details", List.of());
        }

        // Separate baseline (older than yesterday) from recent (yesterday + today)
        List<InsightDaily> baseline = allInsights.stream()
                .filter(i -> i.getDate().isBefore(yesterday))
                .toList();
        List<InsightDaily> recent = allInsights.stream()
                .filter(i -> !i.getDate().isBefore(yesterday))
                .toList();

        if (baseline.isEmpty()) {
            return Map.of("anomaliesDetected", 0, "details", List.of(),
                          "message", "Insufficient baseline data");
        }

        // Group baseline by entity
        Map<String, List<InsightDaily>> baselineByEntity = baseline.stream()
                .collect(Collectors.groupingBy(i -> i.getEntityType() + ":" + i.getEntityId()));

        // Group recent by entity
        Map<String, List<InsightDaily>> recentByEntity = recent.stream()
                .collect(Collectors.groupingBy(i -> i.getEntityType() + ":" + i.getEntityId()));

        // Also need last 3 days for CPM surge check
        LocalDate threeDaysAgo = today.minusDays(CPM_SURGE_LOOKBACK_DAYS);
        Map<String, List<InsightDaily>> last3ByEntity = allInsights.stream()
                .filter(i -> !i.getDate().isBefore(threeDaysAgo))
                .collect(Collectors.groupingBy(i -> i.getEntityType() + ":" + i.getEntityId()));

        List<Map<String, Object>> anomalies = new ArrayList<>();

        Set<String> allEntityKeys = new HashSet<>(baselineByEntity.keySet());
        allEntityKeys.addAll(recentByEntity.keySet());

        for (String entityKey : allEntityKeys) {
            List<InsightDaily> bData = baselineByEntity.getOrDefault(entityKey, List.of());
            List<InsightDaily> rData = recentByEntity.getOrDefault(entityKey, List.of());
            List<InsightDaily> last3 = last3ByEntity.getOrDefault(entityKey, List.of());

            if (bData.isEmpty() || rData.isEmpty()) continue;

            String[] parts = entityKey.split(":");
            String entityType = parts[0];
            UUID entityId = UUID.fromString(parts[1]);
            int baselineDays = (int) bData.stream().map(InsightDaily::getDate).distinct().count();
            if (baselineDays < 3) continue; // need at least 3 days of baseline

            // Compute baseline averages
            double avgDailySpend = bData.stream().mapToDouble(i -> dbl(i.getSpend())).sum() / baselineDays;
            double avgDailyConversions = bData.stream().mapToDouble(i -> dbl(i.getConversions())).sum() / baselineDays;
            double avgCpm = bData.stream().mapToDouble(i -> dbl(i.getCpm())).average().orElse(0);
            double avgCtr = bData.stream().mapToDouble(i -> dbl(i.getCtr())).average().orElse(0);

            // Recent day values (use yesterday or latest available)
            InsightDaily latestRecent = rData.stream()
                    .max(Comparator.comparing(InsightDaily::getDate)).orElse(null);
            if (latestRecent == null) continue;

            double recentSpend = dbl(latestRecent.getSpend());
            double recentConversions = dbl(latestRecent.getConversions());
            double recentCtr = dbl(latestRecent.getCtr());

            // ── Check 1: Spend Spike ──
            if (avgDailySpend > 0 && recentSpend > avgDailySpend * SPEND_SPIKE_FACTOR) {
                double factor = recentSpend / avgDailySpend;
                anomalies.add(createAnomaly(
                        agencyId, clientId, entityType, entityId,
                        "SPEND_SPIKE", "HIGH",
                        String.format("Daily spend $%.2f is %.1f× the 14-day average $%.2f",
                                recentSpend, factor, avgDailySpend),
                        Map.of("alert", "spend_spike", "recentSpend", recentSpend,
                               "avgDailySpend", round(avgDailySpend), "factor", round(factor)),
                        0.95));
            }

            // ── Check 2: Conversion Drop ──
            if (avgDailyConversions >= CONVERSION_DROP_MIN_AVG && recentConversions == 0) {
                anomalies.add(createAnomaly(
                        agencyId, clientId, entityType, entityId,
                        "CONVERSION_DROP", "HIGH",
                        String.format("Zero conversions when 14-day average is %.1f/day",
                                avgDailyConversions),
                        Map.of("alert", "conversion_drop", "recentConversions", 0,
                               "avgDailyConversions", round(avgDailyConversions)),
                        0.90));
            }

            // ── Check 3: CPM Surge (sustained over last 3 days) ──
            if (avgCpm > 0 && last3.size() >= 2) {
                double avgCpmLast3 = last3.stream().mapToDouble(i -> dbl(i.getCpm())).average().orElse(0);
                if (avgCpmLast3 > avgCpm * CPM_SURGE_FACTOR) {
                    double factor = avgCpmLast3 / avgCpm;
                    anomalies.add(createAnomaly(
                            agencyId, clientId, entityType, entityId,
                            "CPM_SURGE", "MEDIUM",
                            String.format("CPM $%.2f (3-day avg) is %.1f× the baseline $%.2f",
                                    avgCpmLast3, factor, avgCpm),
                            Map.of("alert", "cpm_surge", "cpm3d", round(avgCpmLast3),
                                   "cpmBaseline", round(avgCpm), "factor", round(factor)),
                            0.85));
                }
            }

            // ── Check 4: CTR Collapse ──
            if (avgCtr > 0 && recentCtr > 0) {
                double ctrDropPct = (avgCtr - recentCtr) / avgCtr;
                if (ctrDropPct > CTR_COLLAPSE_FACTOR) {
                    anomalies.add(createAnomaly(
                            agencyId, clientId, entityType, entityId,
                            "CTR_COLLAPSE", "MEDIUM",
                            String.format("CTR dropped %.0f%% (%.2f%% → %.2f%%)",
                                    ctrDropPct * 100, avgCtr, recentCtr),
                            Map.of("alert", "ctr_collapse", "recentCtr", round(recentCtr),
                                   "baselineCtr", round(avgCtr), "dropPct", round(ctrDropPct * 100)),
                            0.85));
                }
            }
        }

        log.info("Anomaly Detector: found {} anomalies for client {}", anomalies.size(), clientId);

        // Send alert emails for HIGH severity anomalies
        if (!anomalies.isEmpty()) {
            try {
                sendHighSeverityAlerts(agencyId, clientId, anomalies);
            } catch (Exception e) {
                log.warn("Failed to send anomaly alert emails: {}", e.getMessage());
            }
        }

        // Convert anomaly maps into detail list for the response
        List<Map<String, Object>> details = anomalies.stream()
                .map(a -> {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("type", a.get("_type"));
                    d.put("entityType", a.get("_entityType"));
                    d.put("entityId", a.get("_entityId"));
                    d.put("riskLevel", a.get("_riskLevel"));
                    d.put("description", a.get("_rationale"));
                    return d;
                }).toList();

        return Map.of("anomaliesDetected", anomalies.size(), "details", details);
    }

    // ══════════════════════════════════════════
    // ALERT NOTIFICATIONS
    // ══════════════════════════════════════════

    private void sendHighSeverityAlerts(UUID agencyId, UUID clientId,
                                        List<Map<String, Object>> anomalies) {
        List<Map<String, Object>> highSeverity = anomalies.stream()
                .filter(a -> "HIGH".equals(a.get("_riskLevel")))
                .toList();

        if (highSeverity.isEmpty()) return;

        String clientName = clientRepo.findByIdAndAgencyId(clientId, agencyId)
                .map(Client::getName).orElse("Client");
        String dashboardLink = emailProperties.getBaseUrl() + "/clients";
        List<String> recipients = notificationHelper.getAssignedUserEmails(agencyId, clientId);

        for (Map<String, Object> anomaly : highSeverity) {
            String type = (String) anomaly.get("_type");
            String rationale = (String) anomaly.get("_rationale");

            for (String email : recipients) {
                notificationHelper.sendTemplatedAsync(email,
                        "Alert: " + type.replace("_", " ") + " — " + clientName,
                        "alert",
                        Map.of(
                                "alertTitle", type.replace("_", " ") + " Detected",
                                "alertMessage", rationale,
                                "clientName", clientName,
                                "severity", "HIGH",
                                "severityColor", "#D32F2F",
                                "dashboardLink", dashboardLink
                        ));
            }
        }

        log.info("Queued {} HIGH-severity alert(s) to {} recipient(s) for client {}",
                highSeverity.size(), recipients.size(), clientId);
    }

    // ══════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════

    /**
     * Creates an anomaly entry AND saves it as a DIAGNOSTIC AiSuggestion
     * (with dedup check — skip if same type for same entity within 24h).
     */
    private Map<String, Object> createAnomaly(UUID agencyId, UUID clientId,
                                               String entityType, UUID entityId,
                                               String anomalyType, String riskLevel,
                                               String rationale, Map<String, Object> payload,
                                               double confidence) {
        // Dedup: check if we already created this anomaly type for this entity in the last 24h
        OffsetDateTime oneDayAgo = OffsetDateTime.now().minusHours(24);
        List<AiSuggestion> existing = suggestionRepo
                .findByAgencyIdAndClientIdAndScopeTypeAndScopeIdAndSuggestionTypeAndCreatedAtAfter(
                        agencyId, clientId, entityType, entityId, "DIAGNOSTIC", oneDayAgo);

        // Check if any existing diagnostic has the same anomaly type in its payload
        boolean isDuplicate = existing.stream().anyMatch(s -> {
            String pj = s.getPayloadJson();
            return pj != null && pj.contains("\"alert\":\"" + anomalyType.toLowerCase() + "\"");
        });

        if (!isDuplicate) {
            try {
                String payloadJson = objectMapper.writeValueAsString(payload);
                AiSuggestion suggestion = new AiSuggestion();
                suggestion.setAgencyId(agencyId);
                suggestion.setClientId(clientId);
                suggestion.setScopeType(entityType);
                suggestion.setScopeId(entityId);
                suggestion.setSuggestionType("DIAGNOSTIC");
                suggestion.setPayloadJson(payloadJson);
                suggestion.setRationale("[ANOMALY] " + rationale);
                suggestion.setConfidence(BigDecimal.valueOf(confidence));
                suggestion.setRiskLevel(riskLevel);
                suggestion.setStatus("PENDING");
                suggestion.setCreatedBy("AI");
                suggestion.setCreatedAt(OffsetDateTime.now());
                suggestionRepo.save(suggestion);

                log.info("Anomaly Detector: created {} alert for {} {} (risk: {})",
                        anomalyType, entityType, entityId, riskLevel);
            } catch (Exception e) {
                log.warn("Anomaly Detector: failed to save suggestion: {}", e.getMessage());
            }
        } else {
            log.debug("Anomaly Detector: skipping duplicate {} for {} {}", anomalyType, entityType, entityId);
        }

        // Return metadata for the response (regardless of dedup)
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("_type", anomalyType);
        meta.put("_entityType", entityType);
        meta.put("_entityId", entityId.toString());
        meta.put("_riskLevel", riskLevel);
        meta.put("_rationale", rationale);
        meta.putAll(payload);
        return meta;
    }

    private double dbl(BigDecimal val) {
        return val != null ? val.doubleValue() : 0.0;
    }

    private double round(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
