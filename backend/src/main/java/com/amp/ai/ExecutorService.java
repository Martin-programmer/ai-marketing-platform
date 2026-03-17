package com.amp.ai;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.common.EmailProperties;
import com.amp.common.NotificationHelper;
import com.amp.creatives.CopyVariant;
import com.amp.creatives.CopyVariantRepository;
import com.amp.creatives.CreativePackageItem;
import com.amp.creatives.CreativePackageItemRepository;
import com.amp.creatives.CreativeService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final CreativePackageItemRepository creativePackageItemRepository;
    private final CopyVariantRepository copyVariantRepository;
    private final CreativeService creativeService;
    private final NotificationHelper notificationHelper;
    private final EmailProperties emailProperties;
    private final ClientRepository clientRepo;

    public ExecutorService(AiSuggestionRepository suggestionRepo,
                           AiActionLogRepository actionLogRepo,
                           MetaConnectionRepository metaConnRepo,
                           MetaGraphApiClient metaClient,
                           MetaService metaService,
                           AuditService auditService,
                           ObjectMapper objectMapper,
                           CampaignRepository campaignRepo,
                           AdsetRepository adsetRepo,
                           AdRepository adRepo,
                           CreativePackageItemRepository creativePackageItemRepository,
                           CopyVariantRepository copyVariantRepository,
                           CreativeService creativeService,
                           NotificationHelper notificationHelper,
                           EmailProperties emailProperties,
                           ClientRepository clientRepo) {
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
        this.creativePackageItemRepository = creativePackageItemRepository;
        this.copyVariantRepository = copyVariantRepository;
        this.creativeService = creativeService;
        this.notificationHelper = notificationHelper;
        this.emailProperties = emailProperties;
        this.clientRepo = clientRepo;
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
            String entityId = resolveMetaEntityId(suggestion);
            boolean requiresMetaAction = switch (suggType) {
                case "PAUSE", "ENABLE" -> entityId != null;
                case "BUDGET_ADJUST" -> entityId != null && payload.has("proposed_daily_budget");
                case "DIAGNOSTIC", "CREATIVE_TEST", "COPY_REFRESH" -> false;
                default -> throw new IllegalStateException("Unknown suggestion type: " + suggType);
            };

            String accessToken = null;
            if (requiresMetaAction) {
                MetaConnection conn = metaConnRepo.findByAgencyIdAndClientId(agencyId, suggestion.getClientId())
                        .orElseThrow(() -> new IllegalStateException(
                                "No Meta connection for client " + suggestion.getClientId()));

                if (!"CONNECTED".equals(conn.getStatus())) {
                    throw new IllegalStateException("Meta connection is not active");
                }

                accessToken = metaService.getAccessToken(conn);
            }

            switch (suggType) {
                case "PAUSE" -> {
                    if (entityId != null) {
                        beforeSnapshot = fetchSnapshot(accessToken, entityId);
                        metaResponse   = metaClient.updateAdStatus(accessToken, entityId, "PAUSED");
                        afterSnapshot  = fetchSnapshot(accessToken, entityId);
                    } else {
                        metaResponse = objectMapper.createObjectNode()
                                .put("info", "No Meta entity resolved for PAUSE suggestion");
                    }
                }
                case "ENABLE" -> {
                    if (entityId != null) {
                        beforeSnapshot = fetchSnapshot(accessToken, entityId);
                        metaResponse   = metaClient.updateAdStatus(accessToken, entityId, "ACTIVE");
                        afterSnapshot  = fetchSnapshot(accessToken, entityId);
                    } else {
                        metaResponse = objectMapper.createObjectNode()
                                .put("info", "No Meta entity resolved for ENABLE suggestion");
                    }
                }
                case "BUDGET_ADJUST" -> {
                    if (entityId != null && payload.has("proposed_daily_budget")) {
                        beforeSnapshot = fetchSnapshot(accessToken, entityId);
                        // Meta expects budget in cents (smallest currency unit)
                        long budgetCents = (long) (payload.get("proposed_daily_budget").asDouble() * 100);
                        metaResponse   = metaClient.updateAdsetBudget(accessToken, entityId, budgetCents);
                        afterSnapshot  = fetchSnapshot(accessToken, entityId);
                    } else {
                        metaResponse = objectMapper.createObjectNode()
                                .put("info", "No direct Meta budget update available for this suggestion");
                    }
                }
                case "DIAGNOSTIC", "CREATIVE_TEST", "COPY_REFRESH" -> {
                    // Informational — no Meta API call needed
                    metaResponse = objectMapper.createObjectNode()
                            .put("info", "No Meta action required for " + suggType);
                }
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
    List<Adset> adsets = adsetRepo.findAllByCampaignId(campaignId);
    List<Ad> allAds = new ArrayList<>();
    for (Adset adset : adsets) {
        allAds.addAll(adRepo.findAllByAdsetId(adset.getId()));
    }

        try {
        // 1. Create campaign in Meta as PAUSED
            log.info("Publishing campaign '{}' to Meta account {}", campaign.getName(), adAccountId);
            ObjectNode createCampaignRequest = objectMapper.createObjectNode()
                .put("name", campaign.getName())
                .put("objective", campaign.getObjective())
                .put("status", "PAUSED");
            createCampaignRequest.putArray("special_ad_categories");
            JsonNode metaCampaign = metaClient.createCampaign(
                    accessToken, adAccountId,
                    campaign.getName(), campaign.getObjective(), "PAUSED");

            String metaCampaignId = metaCampaign.get("id").asText();
            campaign.setMetaCampaignId(metaCampaignId);
            campaignRepo.save(campaign);
        saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
            "CAMPAIGN_CREATE",
            createCampaignRequest,
            metaCampaign,
            true,
            objectMapper.createObjectNode().put("meta_campaign_id", metaCampaignId));
            log.info("Created Meta campaign: {}", metaCampaignId);

            // 2. Create adsets
            for (Adset adset : adsets) {
                long budgetCents = adset.getDailyBudget().multiply(java.math.BigDecimal.valueOf(100)).longValue();
        ObjectNode createAdsetRequest = objectMapper.createObjectNode()
            .put("campaign_id", metaCampaignId)
            .put("name", adset.getName())
            .put("daily_budget", budgetCents)
            .put("optimization_goal", adset.getOptimizationGoal() != null ? adset.getOptimizationGoal() : "CONVERSIONS")
            .put("billing_event", "IMPRESSIONS")
            .put("status", "PAUSED");
        JsonNode targetingNode = parseJson(adset.getTargetingJson());
        if (targetingNode != null) {
            createAdsetRequest.set("targeting", targetingNode);
        }

                JsonNode metaAdset = metaClient.createAdset(
                        accessToken, metaCampaignId,
                        adset.getName(), budgetCents,
                        adset.getTargetingJson(),
            adset.getOptimizationGoal() != null ? adset.getOptimizationGoal() : "CONVERSIONS",
            "IMPRESSIONS");

                String metaAdsetId = metaAdset.get("id").asText();
                adset.setMetaAdsetId(metaAdsetId);
                adsetRepo.save(adset);
        saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
            "ADSET_CREATE",
            createAdsetRequest,
            metaAdset,
            true,
            objectMapper.createObjectNode()
                .put("adset_id", adset.getId().toString())
                .put("meta_adset_id", metaAdsetId));
                log.info("Created Meta adset: {}", metaAdsetId);

                // 3. Create ads
        List<Ad> adsForAdset = adRepo.findAllByAdsetId(adset.getId());
        for (Ad ad : adsForAdset) {
            String creativeSpec = buildCreativeSpec(agencyId, ad);
            ObjectNode createAdRequest = objectMapper.createObjectNode()
                .put("adset_id", metaAdsetId)
                .put("name", ad.getName())
                .put("status", "PAUSED");
            createAdRequest.set("creative", parseJson(creativeSpec));

                    JsonNode metaAd = metaClient.createAd(
                            accessToken, metaAdsetId,
                            ad.getName(), creativeSpec);

                    String metaAdId = metaAd.get("id").asText();
                    ad.setMetaAdId(metaAdId);
                    adRepo.save(ad);
            saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
                "AD_CREATE",
                createAdRequest,
                metaAd,
                true,
                objectMapper.createObjectNode()
                    .put("ad_id", ad.getId().toString())
                    .put("meta_ad_id", metaAdId));
                    log.info("Created Meta ad: {}", metaAdId);
                }
            }

        // 4. Activate the created campaign structure after everything exists.
        JsonNode activateCampaignResponse = metaClient.updateCampaignStatus(accessToken, metaCampaignId, "ACTIVE");
        saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
            "CAMPAIGN_ACTIVATE",
            objectMapper.createObjectNode().put("meta_campaign_id", metaCampaignId).put("status", "ACTIVE"),
            activateCampaignResponse,
            true,
            objectMapper.createObjectNode().put("meta_campaign_id", metaCampaignId).put("status", "ACTIVE"));

        for (Adset adset : adsets) {
        JsonNode activateAdsetResponse = metaClient.updateAdsetStatus(accessToken, adset.getMetaAdsetId(), "ACTIVE");
        adset.setStatus("ACTIVE");
        adsetRepo.save(adset);
        saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
            "ADSET_ACTIVATE",
            objectMapper.createObjectNode().put("meta_adset_id", adset.getMetaAdsetId()).put("status", "ACTIVE"),
            activateAdsetResponse,
            true,
            objectMapper.createObjectNode().put("adset_id", adset.getId().toString()).put("status", "ACTIVE"));
        }

        for (Ad ad : allAds) {
        JsonNode activateAdResponse = metaClient.updateAdStatus(accessToken, ad.getMetaAdId(), "ACTIVE");
        ad.setStatus("ACTIVE");
        adRepo.save(ad);
        saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
            "AD_ACTIVATE",
            objectMapper.createObjectNode().put("meta_ad_id", ad.getMetaAdId()).put("status", "ACTIVE"),
            activateAdResponse,
            true,
            objectMapper.createObjectNode().put("ad_id", ad.getId().toString()).put("status", "ACTIVE"));
        }

        // 5. Mark campaign as published in the AMP DB.
            campaign.setStatus("PUBLISHED");
            campaignRepo.save(campaign);
        saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
            "CAMPAIGN_PUBLISH_COMPLETE",
            objectMapper.createObjectNode().put("campaign_id", campaignId.toString()),
            objectMapper.createObjectNode().put("status", "PUBLISHED"),
            true,
            objectMapper.createObjectNode().put("meta_campaign_id", metaCampaignId).put("status", "PUBLISHED"));

            // Audit
            TenantContext ctx = TenantContextHolder.get();
            if (ctx != null) {
                auditService.log(agencyId, campaign.getClientId(),
                        ctx.getUserId(), ctx.getRole(),
                        AuditAction.CAMPAIGN_PUBLISH, "Campaign", campaignId,
                        "DRAFT", "PUBLISHED", null);
            }

            log.info("Successfully published campaign {} to Meta", campaignId);

            // Send campaign published notification
            try {
                String clientName = clientRepo.findByIdAndAgencyId(campaign.getClientId(), agencyId)
                        .map(Client::getName).orElse("Client");
                String dashboardLink = emailProperties.getBaseUrl() + "/campaigns";
                java.util.List<String> recipients = notificationHelper.getAssignedUserEmails(agencyId, campaign.getClientId());
                for (String email : recipients) {
                    notificationHelper.sendTemplatedAsync(email,
                            "Campaign Published \u2014 " + campaign.getName(),
                            "campaign-published",
                            java.util.Map.of(
                                    "campaignName", campaign.getName(),
                                    "clientName", clientName,
                                    "dashboardLink", dashboardLink
                            ));
                }
                log.info("Queued campaign-published notification to {} recipient(s)", recipients.size());
            } catch (Exception notifEx) {
                log.warn("Failed to send campaign-published notification: {}", notifEx.getMessage());
            }

            return Map.of(
                    "status", "PUBLISHED",
                    "campaignId", campaignId,
                    "metaCampaignId", metaCampaignId,
                    "adsetsCreated", adsets.size(),
                    "adsCreated", allAds.size()
            );

        } catch (Exception e) {
            log.error("Failed to publish campaign {}: {}", campaignId, e.getMessage());

            campaign.setStatus("FAILED");
            campaignRepo.save(campaign);
                saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
                    "CAMPAIGN_PUBLISH_FAILED",
                    objectMapper.createObjectNode().put("campaign_id", campaignId.toString()),
                    objectMapper.createObjectNode().put("error", e.getMessage()),
                    false,
                    objectMapper.createObjectNode().put("status", "FAILED"));

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

    private void saveCampaignActionLog(UUID agencyId, UUID clientId, UUID campaignId,
                                       String stage, JsonNode request, JsonNode response,
                                       boolean success, JsonNode snapshot) {
        AiActionLog actionLog = new AiActionLog();
        actionLog.setAgencyId(agencyId);
        actionLog.setClientId(clientId);
        actionLog.setSuggestionId(campaignId);
        actionLog.setExecutedBy("SYSTEM");
        actionLog.setCreatedAt(OffsetDateTime.now());

        ObjectNode requestNode = objectMapper.createObjectNode().put("stage", stage);
        if (request != null) {
            requestNode.set("payload", request);
        }
        ObjectNode responseNode = objectMapper.createObjectNode().put("stage", stage);
        if (response != null) {
            responseNode.set("payload", response);
        }
        ObjectNode snapshotNode = objectMapper.createObjectNode().put("stage", stage);
        if (snapshot != null) {
            snapshotNode.set("result", snapshot);
        }

        actionLog.setMetaRequestJson(requestNode.toString());
        actionLog.setMetaResponseJson(responseNode.toString());
        actionLog.setSuccess(success);
        actionLog.setResultSnapshotJson(snapshotNode.toString());
        actionLogRepo.save(actionLog);
    }

    private String buildCreativeSpec(UUID agencyId, Ad ad) {
        UUID creativeAssetId = ad.getCreativeAssetId();
        UUID copyVariantId = ad.getCopyVariantId();
        String packageCta = null;
        String packageDestinationUrl = null;

        if ((creativeAssetId == null || copyVariantId == null) && ad.getCreativePackageItemId() != null) {
            Optional<CreativePackageItem> packageItem = creativePackageItemRepository.findById(ad.getCreativePackageItemId());
            if (packageItem.isPresent()) {
                if (creativeAssetId == null) {
                    creativeAssetId = packageItem.get().getCreativeAssetId();
                }
                if (copyVariantId == null) {
                    copyVariantId = packageItem.get().getCopyVariantId();
                }
                packageCta = packageItem.get().getCtaType();
                packageDestinationUrl = packageItem.get().getDestinationUrl();
            }
        }

        CopyVariant copyVariant = copyVariantId != null
                ? copyVariantRepository.findById(copyVariantId).orElse(null)
                : null;

        if (copyVariant == null && creativeAssetId != null) {
            List<CopyVariant> variants = copyVariantRepository.findByCreativeAssetIdAndStatusOrderByCreatedAtDesc(creativeAssetId, "APPROVED");
            if (!variants.isEmpty()) {
                copyVariant = variants.get(0);
            }
        }

        String primaryText = firstNonBlank(ad.getPrimaryText(), copyVariant != null ? copyVariant.getPrimaryText() : null);
        String headline = firstNonBlank(ad.getHeadline(), copyVariant != null ? copyVariant.getHeadline() : null, ad.getName());
        String description = firstNonBlank(ad.getDescription(), copyVariant != null ? copyVariant.getDescription() : null);
        String cta = firstNonBlank(ad.getCta(), packageCta, copyVariant != null ? copyVariant.getCta() : null, "LEARN_MORE");
        String destinationUrl = firstNonBlank(ad.getDestinationUrl(), packageDestinationUrl, emailProperties.getBaseUrl());

        ObjectNode creativeNode = objectMapper.createObjectNode();
        if (creativeAssetId != null) {
            try {
                String imageUrl = creativeService.getPresignedViewUrl(agencyId, creativeAssetId);
                creativeNode.put("image_url", imageUrl);
            } catch (Exception e) {
                throw new IllegalStateException("Creative asset not found or unavailable for ad '" + ad.getName() + "': " + creativeAssetId, e);
            }
        }
        creativeNode.put("primary_text", primaryText);
        creativeNode.put("headline", headline);
        creativeNode.put("description", description != null ? description : "");
        creativeNode.put("cta", cta);
        creativeNode.put("url", destinationUrl);
        return creativeNode.toString();
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return objectMapper.createObjectNode().put("raw", value);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
