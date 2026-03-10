package com.amp.ai;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.meta.MetaConnection;
import com.amp.meta.MetaConnectionRepository;
import com.amp.meta.MetaGraphApiClient;
import com.amp.meta.MetaService;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amp.campaigns.Ad;
import com.amp.campaigns.AdRepository;
import com.amp.campaigns.Adset;
import com.amp.campaigns.AdsetRepository;
import com.amp.campaigns.Campaign;
import com.amp.campaigns.CampaignRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies approved AI suggestions to the Meta Ads API.
 * <p>
 * For actionable types (PAUSE, ENABLE, BUDGET_ADJUST) the service makes real
 * Meta Graph API calls, captures before/after snapshots, and records an
 * {@link AiActionLog}.  Informational types (DIAGNOSTIC, CREATIVE_TEST,
 * COPY_REFRESH) are simply marked as APPLIED with no external call.
 */
@Service
public class ExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ExecutorService.class);

    private final AiSuggestionRepository suggestionRepo;
    private final AiActionLogRepository actionLogRepo;
    private final MetaConnectionRepository metaConnRepo;
    private final MetaGraphApiClient metaClient;
    private final MetaService metaService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final CampaignRepository campaignRepo;
    private final AdsetRepository adsetRepo;
    private final AdRepository adRepo;

    public ExecutorService(AiSuggestionRepository suggestionRepo,
                           AiActionLogRepository actionLogRepo,
                           MetaConnectionRepository metaConnRepo,
                           MetaGraphApiClient metaClient,
                           MetaService metaService,
                           AuditService auditService,
                           ObjectMapper objectMapper,
                           CampaignRepository campaignRepo,
                           AdsetRepository adsetRepo,
                           AdRepository adRepo) {
        this.suggestionRepo = suggestionRepo;
        this.actionLogRepo = actionLogRepo;
        this.metaConnRepo = metaConnRepo;
        this.metaClient = metaClient;
        this.metaService = metaService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.campaignRepo = campaignRepo;
        this.adsetRepo = adsetRepo;
        this.adRepo = adRepo;
    }

    // ──────── Public API ────────

    /**
     * Execute an approved suggestion — make the Meta API call.
     */
    @Transactional
    public Map<String, Object> executeSuggestion(UUID agencyId, UUID suggestionId) {

        AiSuggestion suggestion = suggestionRepo.findByIdAndAgencyId(suggestionId, agencyId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + suggestionId));

        if (!"APPROVED".equals(suggestion.getStatus())) {
            throw new IllegalStateException(
                    "Only APPROVED suggestions can be executed. Current status: " + suggestion.getStatus());
        }

        // Get Meta connection for this client
        MetaConnection conn = metaConnRepo.findByAgencyIdAndClientId(agencyId, suggestion.getClientId())
                .orElseThrow(() -> new IllegalStateException(
                        "No Meta connection for client " + suggestion.getClientId()));

        if (!"CONNECTED".equals(conn.getStatus())) {
            throw new IllegalStateException("Meta connection is not active");
        }

        String accessToken = metaService.getAccessToken(conn);

        // Transition: APPROVED → APPLYING
        suggestion.setStatus("APPLYING");
        suggestionRepo.save(suggestion);

        // Prepare action log
        AiActionLog actionLog = new AiActionLog();
        actionLog.setAgencyId(agencyId);
        actionLog.setClientId(suggestion.getClientId());
        actionLog.setSuggestionId(suggestionId);
        actionLog.setExecutedBy("SYSTEM");
        actionLog.setCreatedAt(OffsetDateTime.now());

        try {
            JsonNode payload = objectMapper.readTree(suggestion.getPayloadJson());
            JsonNode beforeSnapshot = null;
            JsonNode afterSnapshot  = null;
            JsonNode metaResponse   = null;

            String suggType = suggestion.getSuggestionType();

            switch (suggType) {
                case "PAUSE" -> {
                    String entityId = resolveMetaEntityId(suggestion);
                    if (entityId != null) {
                        beforeSnapshot = fetchSnapshot(accessToken, entityId);
                        metaResponse   = metaClient.updateAdStatus(accessToken, entityId, "PAUSED");
                        afterSnapshot  = fetchSnapshot(accessToken, entityId);
                    }
                }
                case "ENABLE" -> {
                    String entityId = resolveMetaEntityId(suggestion);
                    if (entityId != null) {
                        beforeSnapshot = fetchSnapshot(accessToken, entityId);
                        metaResponse   = metaClient.updateAdStatus(accessToken, entityId, "ACTIVE");
                        afterSnapshot  = fetchSnapshot(accessToken, entityId);
                    }
                }
                case "BUDGET_ADJUST" -> {
                    String entityId = resolveMetaEntityId(suggestion);
                    if (entityId != null && payload.has("proposed_daily_budget")) {
                        beforeSnapshot = fetchSnapshot(accessToken, entityId);
                        // Meta expects budget in cents (smallest currency unit)
                        long budgetCents = (long) (payload.get("proposed_daily_budget").asDouble() * 100);
                        metaResponse   = metaClient.updateAdsetBudget(accessToken, entityId, budgetCents);
                        afterSnapshot  = fetchSnapshot(accessToken, entityId);
                    }
                }
                case "DIAGNOSTIC", "CREATIVE_TEST", "COPY_REFRESH" -> {
                    // Informational — no Meta API call needed
                    metaResponse = objectMapper.createObjectNode()
                            .put("info", "No Meta action required for " + suggType);
                }
                default -> throw new IllegalStateException("Unknown suggestion type: " + suggType);
            }

            // ── Record success ──
            actionLog.setMetaRequestJson(suggestion.getPayloadJson());
            actionLog.setMetaResponseJson(metaResponse != null ? metaResponse.toString() : "{}");
            actionLog.setSuccess(true);

            ObjectNode snapshot = objectMapper.createObjectNode();
            if (beforeSnapshot != null) snapshot.set("before", beforeSnapshot);
            if (afterSnapshot  != null) snapshot.set("after",  afterSnapshot);
            actionLog.setResultSnapshotJson(snapshot.toString());

            actionLogRepo.save(actionLog);

            // Transition: APPLYING → APPLIED
            suggestion.setStatus("APPLIED");
            suggestionRepo.save(suggestion);

            // Audit trail
            TenantContext ctx = TenantContextHolder.get();
            if (ctx != null) {
                auditService.log(agencyId, suggestion.getClientId(),
                        ctx.getUserId(), ctx.getRole(),
                        AuditAction.SUGGESTION_APPLY, "AiSuggestion", suggestionId,
                        "APPROVED", "APPLIED", null);
            }

            log.info("Successfully executed suggestion {} (type: {})", suggestionId, suggType);

            return Map.of(
                    "status", "APPLIED",
                    "suggestionId", suggestionId,
                    "type", suggType,
                    "metaResponse", metaResponse != null ? metaResponse.toString() : "{}"
            );

        } catch (Exception e) {
            log.error("Failed to execute suggestion {}: {}", suggestionId, e.getMessage());

            // ── Record failure ──
            actionLog.setMetaRequestJson(suggestion.getPayloadJson());
            actionLog.setMetaResponseJson(
                    "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            actionLog.setSuccess(false);
            actionLogRepo.save(actionLog);

            // Transition: APPLYING → FAILED
            suggestion.setStatus("FAILED");
            suggestionRepo.save(suggestion);

            return Map.of(
                    "status", "FAILED",
                    "suggestionId", suggestionId,
                    "error", e.getMessage()
            );
        }
    }

    // ──────── Campaign publish ────────

    /**
     * Publish a DRAFT campaign to Meta: creates campaign → adsets → ads sequentially.
     * Saves Meta IDs back and transitions to PUBLISHED (or FAILED).
     */
    @Transactional
    public Map<String, Object> publishCampaign(UUID agencyId, UUID campaignId) {
        Campaign campaign = campaignRepo.findByIdAndAgencyId(campaignId, agencyId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        if (!"DRAFT".equals(campaign.getStatus())) {
            throw new IllegalStateException(
                    "Only DRAFT campaigns can be published. Current status: " + campaign.getStatus());
        }

        MetaConnection conn = metaConnRepo.findByAgencyIdAndClientId(agencyId, campaign.getClientId())
                .orElseThrow(() -> new IllegalStateException(
                        "No Meta connection for client " + campaign.getClientId()));

        if (!"CONNECTED".equals(conn.getStatus())) {
            throw new IllegalStateException("Meta connection is not active");
        }

        String accessToken = metaService.getAccessToken(conn);
        String adAccountId = conn.getAdAccountId();

        AiActionLog actionLog = new AiActionLog();
        actionLog.setAgencyId(agencyId);
        actionLog.setClientId(campaign.getClientId());
        actionLog.setSuggestionId(campaignId); // re-use field for campaign reference
        actionLog.setExecutedBy("SYSTEM");
        actionLog.setCreatedAt(OffsetDateTime.now());

        try {
            // 1. Create campaign in Meta
            log.info("Publishing campaign '{}' to Meta account {}", campaign.getName(), adAccountId);
            JsonNode metaCampaign = metaClient.createCampaign(
                    accessToken, adAccountId,
                    campaign.getName(), campaign.getObjective(), "PAUSED");

            String metaCampaignId = metaCampaign.get("id").asText();
            campaign.setMetaCampaignId(metaCampaignId);
            campaignRepo.save(campaign);
            log.info("Created Meta campaign: {}", metaCampaignId);

            // 2. Create adsets
            List<Adset> adsets = adsetRepo.findAllByCampaignId(campaignId);
            for (Adset adset : adsets) {
                long budgetCents = adset.getDailyBudget().multiply(java.math.BigDecimal.valueOf(100)).longValue();
                JsonNode metaAdset = metaClient.createAdset(
                        accessToken, metaCampaignId,
                        adset.getName(), budgetCents,
                        adset.getTargetingJson(),
                        "CONVERSIONS", "IMPRESSIONS");

                String metaAdsetId = metaAdset.get("id").asText();
                adset.setMetaAdsetId(metaAdsetId);
                adsetRepo.save(adset);
                log.info("Created Meta adset: {}", metaAdsetId);

                // 3. Create ads
                List<Ad> ads = adRepo.findAllByAdsetId(adset.getId());
                for (Ad ad : ads) {
                    // Build minimal creative spec
                    String creativeSpec = "{\"creative\":{\"name\":\"" + ad.getName() + "\"}}";
                    JsonNode metaAd = metaClient.createAd(
                            accessToken, metaAdsetId,
                            ad.getName(), creativeSpec);

                    String metaAdId = metaAd.get("id").asText();
                    ad.setMetaAdId(metaAdId);
                    adRepo.save(ad);
                    log.info("Created Meta ad: {}", metaAdId);
                }
            }

            // Mark campaign as PUBLISHED
            campaign.setStatus("PUBLISHED");
            campaignRepo.save(campaign);

            // Record success
            actionLog.setMetaRequestJson("{\"campaign_id\":\"" + metaCampaignId + "\"}");
            actionLog.setMetaResponseJson("{\"status\":\"PUBLISHED\"}");
            actionLog.setSuccess(true);
            actionLog.setResultSnapshotJson("{\"meta_campaign_id\":\"" + metaCampaignId + "\"}");
            actionLogRepo.save(actionLog);

            // Audit
            TenantContext ctx = TenantContextHolder.get();
            if (ctx != null) {
                auditService.log(agencyId, campaign.getClientId(),
                        ctx.getUserId(), ctx.getRole(),
                        AuditAction.CAMPAIGN_PUBLISH, "Campaign", campaignId,
                        "DRAFT", "PUBLISHED", null);
            }

            log.info("Successfully published campaign {} to Meta", campaignId);

            return Map.of(
                    "status", "PUBLISHED",
                    "campaignId", campaignId,
                    "metaCampaignId", metaCampaignId,
                    "adsetsCreated", adsets.size()
            );

        } catch (Exception e) {
            log.error("Failed to publish campaign {}: {}", campaignId, e.getMessage());

            campaign.setStatus("FAILED");
            campaignRepo.save(campaign);

            actionLog.setMetaRequestJson("{}");
            actionLog.setMetaResponseJson(
                    "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            actionLog.setSuccess(false);
            actionLogRepo.save(actionLog);

            return Map.of(
                    "status", "FAILED",
                    "campaignId", campaignId,
                    "error", e.getMessage()
            );
        }
    }

    // ──────── Helpers ────────

    /**
     * Resolve the Meta entity ID from a suggestion's payload or scope.
     */
    private String resolveMetaEntityId(AiSuggestion suggestion) {
        try {
            JsonNode payload = objectMapper.readTree(suggestion.getPayloadJson());

            // Explicit meta entity ID in payload takes priority
            if (payload.has("meta_entity_id")) {
                return payload.get("meta_entity_id").asText();
            }

            // Budget reallocations need special handling — skip for now
            if (payload.has("from_entity")) {
                return null;
            }

            // Fallback: use scope_id as Meta ID (works when we store Meta IDs as scope)
            return suggestion.getScopeId().toString();
        } catch (Exception e) {
            log.warn("Could not resolve Meta entity ID for suggestion {}", suggestion.getId());
            return null;
        }
    }

    private JsonNode fetchSnapshot(String accessToken, String entityId) {
        try {
            return metaClient.getEntityDetails(accessToken, entityId,
                    "id,name,status,daily_budget,lifetime_budget");
        } catch (Exception e) {
            log.warn("Could not fetch snapshot for entity {}: {}", entityId, e.getMessage());
            return null;
        }
    }
}
