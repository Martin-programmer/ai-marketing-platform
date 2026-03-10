package com.amp.meta;

import com.amp.ai.AnomalyDetectorService;
import com.amp.campaigns.Ad;
import com.amp.campaigns.AdRepository;
import com.amp.campaigns.Adset;
import com.amp.campaigns.AdsetRepository;
import com.amp.campaigns.Campaign;
import com.amp.campaigns.CampaignRepository;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.amp.tenancy.TenantContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * Service that orchestrates fetching campaigns, adsets, ads, and insights
 * from the Meta Graph API and upserting them into our database.
 * <p>
 * MVP: runs synchronously. Future: async via SQS worker.
 */
@Service
public class MetaSyncService {

    private static final Logger log = LoggerFactory.getLogger(MetaSyncService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final MetaGraphApiClient graphApiClient;
    private final MetaService metaService;
    private final MetaConnectionRepository connectionRepository;
    private final MetaSyncJobRepository syncJobRepository;
    private final CampaignRepository campaignRepository;
    private final AdsetRepository adsetRepository;
    private final AdRepository adRepository;
    private final InsightDailyRepository insightDailyRepository;
    private final AnomalyDetectorService anomalyDetector;

    public MetaSyncService(MetaGraphApiClient graphApiClient,
                           MetaService metaService,
                           MetaConnectionRepository connectionRepository,
                           MetaSyncJobRepository syncJobRepository,
                           CampaignRepository campaignRepository,
                           AdsetRepository adsetRepository,
                           AdRepository adRepository,
                           InsightDailyRepository insightDailyRepository,
                           AnomalyDetectorService anomalyDetector) {
        this.graphApiClient = graphApiClient;
        this.metaService = metaService;
        this.connectionRepository = connectionRepository;
        this.syncJobRepository = syncJobRepository;
        this.campaignRepository = campaignRepository;
        this.adsetRepository = adsetRepository;
        this.adRepository = adRepository;
        this.insightDailyRepository = insightDailyRepository;
        this.anomalyDetector = anomalyDetector;
    }

    // ──────── Public entry points ────────

    /**
     * Run initial sync for a client: fetch all structure + last 90 days insights.
     */
    @Transactional
    public MetaSyncJob runInitialSync(UUID agencyId, UUID clientId) {
        return runSync(agencyId, clientId, "INITIAL", 90);
    }

    /**
     * Run daily sync for a client: fetch structure + last 7 days insights (reconciliation).
     */
    @Transactional
    public MetaSyncJob runDailySync(UUID agencyId, UUID clientId) {
        return runSync(agencyId, clientId, "DAILY", 7);
    }

    /**
     * Run manual sync triggered by user: last 30 days.
     */
    @Transactional
    public MetaSyncJob runManualSync(UUID agencyId, UUID clientId) {
        return runSync(agencyId, clientId, "MANUAL", 30);
    }

    /**
     * Return the 10 most recent sync jobs for a client.
     */
    @Transactional(readOnly = true)
    public List<MetaSyncJobResponse> getRecentJobs(UUID agencyId, UUID clientId) {
        return syncJobRepository
                .findTop10ByAgencyIdAndClientIdOrderByRequestedAtDesc(agencyId, clientId)
                .stream().map(MetaSyncJobResponse::from).toList();
    }

    // ──────── Core sync logic ────────

    private MetaSyncJob runSync(UUID agencyId, UUID clientId, String jobType, int daysBack) {
        MetaConnection conn = connectionRepository.findByAgencyIdAndClientId(agencyId, clientId)
                .orElseThrow(() -> new IllegalStateException("No Meta connection for client " + clientId));

        if (!"CONNECTED".equals(conn.getStatus())) {
            throw new IllegalStateException("Meta connection is not active: " + conn.getStatus());
        }

        String accessToken = metaService.getAccessToken(conn);
        String adAccountId = conn.getAdAccountId();

        // Create sync job
        String idempotencyKey = String.format("sync-%s-%s-%s-%s",
                clientId, jobType.toLowerCase(), LocalDate.now(),
                UUID.randomUUID().toString().substring(0, 8));

        MetaSyncJob job = new MetaSyncJob();
        job.setAgencyId(agencyId);
        job.setClientId(clientId);
        job.setJobType(jobType);
        job.setJobStatus("RUNNING");
        job.setIdempotencyKey(idempotencyKey);
        job.setRequestedAt(OffsetDateTime.now());
        job.setStartedAt(OffsetDateTime.now());
        job = syncJobRepository.save(job);

        Map<String, Object> stats = new LinkedHashMap<>();

        try {
            // 1. Sync campaigns
            List<JsonNode> metaCampaigns = graphApiClient.getCampaigns(accessToken, adAccountId);
            Map<String, UUID> campaignMap = syncCampaigns(agencyId, clientId, metaCampaigns);
            stats.put("campaigns", campaignMap.size());
            log.info("Synced {} campaigns for client {}", campaignMap.size(), clientId);

            // 2. Sync adsets
            List<JsonNode> metaAdSets = graphApiClient.getAdSets(accessToken, adAccountId);
            Map<String, UUID> adsetMap = syncAdsets(agencyId, clientId, metaAdSets, campaignMap);
            stats.put("adsets", adsetMap.size());
            log.info("Synced {} adsets for client {}", adsetMap.size(), clientId);

            // 3. Sync ads
            List<JsonNode> metaAds = graphApiClient.getAds(accessToken, adAccountId);
            Map<String, UUID> adMap = syncAds(agencyId, clientId, metaAds, adsetMap);
            stats.put("ads", adMap.size());
            log.info("Synced {} ads for client {}", adMap.size(), clientId);

            // 4. Sync insights
            String dateTo = LocalDate.now().toString();
            String dateFrom = LocalDate.now().minusDays(daysBack).toString();
            List<JsonNode> insights = graphApiClient.getAccountInsights(
                    accessToken, adAccountId, dateFrom, dateTo);
            int insightCount = syncInsights(agencyId, clientId, insights,
                    campaignMap, adsetMap, adMap);
            stats.put("insights_days", insightCount);
            log.info("Synced {} insight records for client {}", insightCount, clientId);

            // Update job → SUCCESS
            job.setJobStatus("SUCCESS");
            job.setFinishedAt(OffsetDateTime.now());
            job.setStatsJson(toJson(stats));
            syncJobRepository.save(job);

            // Update connection last_sync_at
            conn.setLastSyncAt(OffsetDateTime.now());
            conn.setLastErrorCode(null);
            conn.setLastErrorMessage(null);
            conn.setUpdatedAt(OffsetDateTime.now());
            connectionRepository.save(conn);

            // Run anomaly detection on fresh data (no-fail — don't break the sync)
            try {
                anomalyDetector.detectAnomalies(agencyId, clientId);
            } catch (Exception anomalyEx) {
                log.warn("Anomaly detection failed for client {} (sync still OK): {}",
                        clientId, anomalyEx.getMessage());
            }

            return job;

        } catch (Exception e) {
            log.error("Sync failed for client {}: {}", clientId, e.getMessage(), e);

            job.setJobStatus("FAILED");
            job.setFinishedAt(OffsetDateTime.now());
            job.setErrorJson("{\"error\":\"" + escapeJson(e.getMessage())
                    + "\",\"type\":\"" + e.getClass().getSimpleName() + "\"}");
            syncJobRepository.save(job);

            conn.setLastErrorCode("SYNC_FAILED");
            conn.setLastErrorMessage(e.getMessage());
            conn.setUpdatedAt(OffsetDateTime.now());
            connectionRepository.save(conn);

            throw new RuntimeException("Sync failed: " + e.getMessage(), e);
        }
    }

    // ──────── Entity sync helpers ────────

    /**
     * Upsert campaigns from Meta. Returns map of metaCampaignId → our internal UUID.
     */
    private Map<String, UUID> syncCampaigns(UUID agencyId, UUID clientId,
                                            List<JsonNode> metaCampaigns) {
        Map<String, UUID> campaignMap = new HashMap<>();
        UUID currentUserId = TenantContextHolder.require().getUserId();

        for (JsonNode mc : metaCampaigns) {
            String metaCampaignId = mc.get("id").asText();
            String name = textOrDefault(mc, "name", "Unnamed");
            String objective = textOrDefault(mc, "objective", "UNKNOWN");
            String status = textOrDefault(mc, "status", "UNKNOWN");
            String mappedStatus = mapMetaStatus(status);

            Optional<Campaign> existing = campaignRepository
                    .findByAgencyIdAndClientIdAndMetaCampaignId(agencyId, clientId, metaCampaignId);

            Campaign campaign;
            if (existing.isPresent()) {
                campaign = existing.get();
                campaign.setName(name);
                campaign.setObjective(objective);
                campaign.setStatus(mappedStatus);
            } else {
                campaign = new Campaign();
                campaign.setAgencyId(agencyId);
                campaign.setClientId(clientId);
                campaign.setPlatform("META");
                campaign.setMetaCampaignId(metaCampaignId);
                campaign.setName(name);
                campaign.setObjective(objective);
                campaign.setStatus(mappedStatus);
                campaign.setCreatedBy(currentUserId);
                campaign.setCreatedAt(OffsetDateTime.now());
                campaign.setUpdatedAt(OffsetDateTime.now());
            }
            campaign = campaignRepository.save(campaign);
            campaignMap.put(metaCampaignId, campaign.getId());
        }
        return campaignMap;
    }

    /**
     * Upsert adsets from Meta. Returns map of metaAdsetId → our internal UUID.
     */
    private Map<String, UUID> syncAdsets(UUID agencyId, UUID clientId,
                                         List<JsonNode> metaAdSets,
                                         Map<String, UUID> campaignMap) {
        Map<String, UUID> adsetMap = new HashMap<>();

        for (JsonNode ma : metaAdSets) {
            String metaAdsetId = ma.get("id").asText();
            String name = textOrDefault(ma, "name", "Unnamed");
            String status = textOrDefault(ma, "status", "UNKNOWN");
            String metaCampaignId = ma.has("campaign_id") ? ma.get("campaign_id").asText() : null;

            UUID campaignId = metaCampaignId != null ? campaignMap.get(metaCampaignId) : null;
            if (campaignId == null) {
                log.warn("Skipping adset {} — no matching campaign for meta campaign_id {}",
                        metaAdsetId, metaCampaignId);
                continue;
            }

            String mappedStatus = mapMetaStatus(status);

            // Meta returns budget in cents
            BigDecimal dailyBudget = getBigDecimal(ma, "daily_budget").movePointLeft(2);
            if (dailyBudget.compareTo(BigDecimal.ZERO) == 0) {
                dailyBudget = getBigDecimal(ma, "lifetime_budget").movePointLeft(2);
            }
            if (dailyBudget.compareTo(BigDecimal.ZERO) == 0) {
                dailyBudget = BigDecimal.ONE; // fallback — column is NOT NULL
            }

            String targetingJson = ma.has("targeting") ? ma.get("targeting").toString() : "{}";

            Optional<Adset> existing = adsetRepository
                    .findByAgencyIdAndClientIdAndMetaAdsetId(agencyId, clientId, metaAdsetId);

            Adset adset;
            if (existing.isPresent()) {
                adset = existing.get();
                adset.setName(name);
                adset.setStatus(mappedStatus);
                adset.setCampaignId(campaignId);
                adset.setDailyBudget(dailyBudget);
                adset.setTargetingJson(targetingJson);
            } else {
                adset = new Adset();
                adset.setAgencyId(agencyId);
                adset.setClientId(clientId);
                adset.setCampaignId(campaignId);
                adset.setMetaAdsetId(metaAdsetId);
                adset.setName(name);
                adset.setDailyBudget(dailyBudget);
                adset.setTargetingJson(targetingJson);
                adset.setStatus(mappedStatus);
                adset.setCreatedAt(OffsetDateTime.now());
                adset.setUpdatedAt(OffsetDateTime.now());
            }
            adset = adsetRepository.save(adset);
            adsetMap.put(metaAdsetId, adset.getId());
        }
        return adsetMap;
    }

    /**
     * Upsert ads from Meta. Returns map of metaAdId → our internal UUID.
     */
    private Map<String, UUID> syncAds(UUID agencyId, UUID clientId,
                                       List<JsonNode> metaAds,
                                       Map<String, UUID> adsetMap) {
        Map<String, UUID> adMap = new HashMap<>();

        for (JsonNode ma : metaAds) {
            String metaAdId = ma.get("id").asText();
            String name = textOrDefault(ma, "name", "Unnamed");
            String status = textOrDefault(ma, "status", "UNKNOWN");
            String metaAdsetId = ma.has("adset_id") ? ma.get("adset_id").asText() : null;

            UUID adsetId = metaAdsetId != null ? adsetMap.get(metaAdsetId) : null;
            if (adsetId == null) {
                log.warn("Skipping ad {} — no matching adset for meta adset_id {}",
                        metaAdId, metaAdsetId);
                continue;
            }

            String mappedStatus = mapMetaStatus(status);

            Optional<Ad> existing = adRepository
                    .findByAgencyIdAndClientIdAndMetaAdId(agencyId, clientId, metaAdId);

            Ad ad;
            if (existing.isPresent()) {
                ad = existing.get();
                ad.setName(name);
                ad.setStatus(mappedStatus);
                ad.setAdsetId(adsetId);
            } else {
                ad = new Ad();
                ad.setAgencyId(agencyId);
                ad.setClientId(clientId);
                ad.setAdsetId(adsetId);
                ad.setMetaAdId(metaAdId);
                ad.setName(name);
                ad.setStatus(mappedStatus);
                ad.setCreatedAt(OffsetDateTime.now());
                ad.setUpdatedAt(OffsetDateTime.now());
            }
            ad = adRepository.save(ad);
            adMap.put(metaAdId, ad.getId());
        }
        return adMap;
    }

    /**
     * Upsert daily insights from Meta.
     * Ad-level insights are mapped to our internal entity UUIDs,
     * falling back to adset → campaign if the ad mapping is missing.
     */
    private int syncInsights(UUID agencyId, UUID clientId, List<JsonNode> insights,
                             Map<String, UUID> campaignMap,
                             Map<String, UUID> adsetMap,
                             Map<String, UUID> adMap) {
        int count = 0;
        for (JsonNode insight : insights) {
            try {
                String dateStr = insight.has("date_start") ? insight.get("date_start").asText() : null;
                if (dateStr == null) continue;

                LocalDate date = LocalDate.parse(dateStr);

                // Resolve entity: ad → adset → campaign
                String entityType = null;
                UUID entityId = null;

                if (insight.has("ad_id")) {
                    entityId = adMap.get(insight.get("ad_id").asText());
                    if (entityId != null) entityType = "AD";
                }
                if (entityId == null && insight.has("adset_id")) {
                    entityId = adsetMap.get(insight.get("adset_id").asText());
                    if (entityId != null) entityType = "ADSET";
                }
                if (entityId == null && insight.has("campaign_id")) {
                    entityId = campaignMap.get(insight.get("campaign_id").asText());
                    if (entityId != null) entityType = "CAMPAIGN";
                }
                if (entityId == null) {
                    log.debug("Skipping insight — no matching entity for date {}", dateStr);
                    continue;
                }

                BigDecimal impressions = getBigDecimal(insight, "impressions");
                BigDecimal clicks = getBigDecimal(insight, "clicks");
                BigDecimal spend = getBigDecimal(insight, "spend");
                BigDecimal cpc = getBigDecimal(insight, "cpc");
                BigDecimal cpm = getBigDecimal(insight, "cpm");
                BigDecimal ctr = getBigDecimal(insight, "ctr");

                // Extract conversions and conversion values from actions array
                BigDecimal conversions = BigDecimal.ZERO;
                BigDecimal conversionValue = BigDecimal.ZERO;

                if (insight.has("actions") && insight.get("actions").isArray()) {
                    for (JsonNode action : insight.get("actions")) {
                        String actionType = action.has("action_type")
                                ? action.get("action_type").asText() : "";
                        if ("offsite_conversion".equals(actionType)
                                || "purchase".equals(actionType)) {
                            conversions = conversions.add(getBigDecimal(action, "value"));
                        }
                    }
                }
                if (insight.has("action_values") && insight.get("action_values").isArray()) {
                    for (JsonNode av : insight.get("action_values")) {
                        String actionType = av.has("action_type")
                                ? av.get("action_type").asText() : "";
                        if ("offsite_conversion".equals(actionType)
                                || "purchase".equals(actionType)) {
                            conversionValue = conversionValue.add(getBigDecimal(av, "value"));
                        }
                    }
                }

                BigDecimal roas = spend.compareTo(BigDecimal.ZERO) > 0
                        ? conversionValue.divide(spend, 6, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                BigDecimal frequency = getBigDecimal(insight, "frequency");
                long reach = insight.has("reach") ? insight.get("reach").asLong() : 0L;

                // Upsert
                Optional<InsightDaily> existingInsight = insightDailyRepository
                        .findByAgencyIdAndClientIdAndEntityTypeAndEntityIdAndDate(
                                agencyId, clientId, entityType, entityId, date);

                InsightDaily daily;
                if (existingInsight.isPresent()) {
                    daily = existingInsight.get();
                } else {
                    daily = new InsightDaily();
                    daily.setAgencyId(agencyId);
                    daily.setClientId(clientId);
                    daily.setEntityType(entityType);
                    daily.setEntityId(entityId);
                    daily.setDate(date);
                    daily.setCreatedAt(OffsetDateTime.now());
                }

                daily.setImpressions(impressions.longValue());
                daily.setClicks(clicks.longValue());
                daily.setSpend(spend);
                daily.setConversions(conversions);
                daily.setConversionValue(conversionValue);
                daily.setCpc(cpc);
                daily.setCpm(cpm);
                daily.setCtr(ctr);
                daily.setRoas(roas);
                daily.setFrequency(frequency);
                daily.setReach(reach);
                daily.setRawJson(insight.toString());

                insightDailyRepository.save(daily);
                count++;

            } catch (Exception e) {
                log.warn("Failed to parse insight record: {}", e.getMessage());
            }
        }
        return count;
    }

    // ──────── Helpers ────────

    private String mapMetaStatus(String metaStatus) {
        return switch (metaStatus.toUpperCase()) {
            case "ACTIVE" -> "PUBLISHED";
            case "PAUSED" -> "PAUSED";
            case "ARCHIVED", "DELETED" -> "ARCHIVED";
            default -> "DRAFT";
        };
    }

    private BigDecimal getBigDecimal(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            try {
                return new BigDecimal(node.get(field).asText());
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        return node.has(field) ? node.get(field).asText() : defaultValue;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
