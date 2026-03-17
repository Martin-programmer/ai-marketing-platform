package com.amp.ai;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.common.EmailProperties;
import com.amp.common.NotificationHelper;
import com.amp.creatives.CopyVariant;
import com.amp.creatives.CopyVariantRepository;
import com.amp.creatives.CreativeAsset;
import com.amp.creatives.CreativeAssetRepository;
import com.amp.creatives.CreativePackageItem;
import com.amp.creatives.CreativePackageItemRepository;
import com.amp.creatives.CreativeService;
import com.amp.creatives.S3StorageService;
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
    private final CreativeAssetRepository creativeAssetRepository;
    private final S3StorageService s3StorageService;
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
                           CreativeAssetRepository creativeAssetRepository,
                           S3StorageService s3StorageService,
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
        this.creativeAssetRepository = creativeAssetRepository;
        this.s3StorageService = s3StorageService;
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
     * Publish a DRAFT campaign to Meta using the proper sequence:
     * 1. Create campaign (PAUSED)
     * 2. For each adset: create adset (PAUSED)
     * 3. For each ad: upload image → create ad creative → create ad (PAUSED)
     * 4. Activate everything (campaign + adsets + ads → ACTIVE)
     * 5. Mark PUBLISHED in our DB
     *
     * Returns a detailed step-by-step result map including progress steps.
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

        String pageId = conn.getPageId();
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalStateException(
                    "No Facebook Page linked. Please reconnect Meta with Page access.");
        }

        String accessToken = metaService.getAccessToken(conn);
        String adAccountId = conn.getAdAccountId();

        List<Adset> adsets = adsetRepo.findAllByCampaignId(campaignId);
        List<Map<String, String>> steps = new ArrayList<>();

        String metaCampaignId = null;

        try {
            // ── Step 1: Create campaign in Meta as PAUSED ──
            log.info("Publishing campaign '{}' to Meta account {}", campaign.getName(), adAccountId);
            JsonNode metaCampaign = metaClient.createCampaign(
                    accessToken, adAccountId,
                    campaign.getName(), campaign.getObjective(), "PAUSED");
            metaCampaignId = metaCampaign.get("id").asText();
            campaign.setMetaCampaignId(metaCampaignId);
            campaignRepo.save(campaign);
            saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
                    "CAMPAIGN_CREATE",
                    objectMapper.createObjectNode()
                            .put("name", campaign.getName())
                            .put("objective", campaign.getObjective()),
                    metaCampaign, true,
                    objectMapper.createObjectNode().put("meta_campaign_id", metaCampaignId));
            steps.add(Map.of("step", "campaign", "status", "done",
                    "message", "Created campaign: " + metaCampaignId));
            log.info("Created Meta campaign: {}", metaCampaignId);

            // ── Step 2: Create adsets ──
            int adsetIdx = 0;
            int totalAdsCreated = 0;
            for (Adset adset : adsets) {
                adsetIdx++;
                long budgetCents = adset.getDailyBudget()
                        .multiply(java.math.BigDecimal.valueOf(100)).longValue();

                JsonNode metaAdset;
                try {
                    metaAdset = metaClient.createAdset(
                            accessToken, metaCampaignId,
                            adset.getName(), budgetCents,
                            adset.getTargetingJson(),
                            adset.getOptimizationGoal() != null
                                    ? adset.getOptimizationGoal() : "OFFSITE_CONVERSIONS",
                            "IMPRESSIONS", "PAUSED", null);
                } catch (Exception adsetEx) {
                    log.error("Failed to create adset '{}' in Meta: {}", adset.getName(), adsetEx.getMessage());
                    // Cleanup: delete the Meta campaign we already created
                    tryDeleteMetaCampaign(accessToken, metaCampaignId);
                    markCampaignFailed(agencyId, campaign, campaignId,
                            "Failed to create adset '" + adset.getName() + "': " + adsetEx.getMessage());
                    return Map.of(
                            "status", "FAILED",
                            "campaignId", campaignId,
                            "error", "Adset creation failed: " + adsetEx.getMessage(),
                            "failedStep", "adset_" + adsetIdx,
                            "steps", steps);
                }

                String metaAdsetId = metaAdset.get("id").asText();
                adset.setMetaAdsetId(metaAdsetId);
                adsetRepo.save(adset);
                saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
                        "ADSET_CREATE",
                        objectMapper.createObjectNode()
                                .put("name", adset.getName())
                                .put("daily_budget", budgetCents),
                        metaAdset, true,
                        objectMapper.createObjectNode()
                                .put("adset_id", adset.getId().toString())
                                .put("meta_adset_id", metaAdsetId));
                steps.add(Map.of("step", "adset", "status", "done",
                        "message", "Created adset " + adsetIdx + " of " + adsets.size()));
                log.info("Created Meta adset: {}", metaAdsetId);

                // ── Step 3: Create ads for this adset ──
                List<Ad> adsForAdset = adRepo.findAllByAdsetId(adset.getId());
                int adIdx = 0;
                for (Ad ad : adsForAdset) {
                    adIdx++;
                    try {
                        // 3a. Resolve creative asset & copy variant
                        UUID creativeAssetId = ad.getCreativeAssetId();
                        UUID copyVariantId = ad.getCopyVariantId();

                        // Fallback to package item if direct fields are null
                        if ((creativeAssetId == null || copyVariantId == null)
                                && ad.getCreativePackageItemId() != null) {
                            Optional<CreativePackageItem> pkgItem =
                                    creativePackageItemRepository.findById(ad.getCreativePackageItemId());
                            if (pkgItem.isPresent()) {
                                if (creativeAssetId == null) creativeAssetId = pkgItem.get().getCreativeAssetId();
                                if (copyVariantId == null) copyVariantId = pkgItem.get().getCopyVariantId();
                            }
                        }

                        // Resolve copy variant text (ad fields take priority)
                        CopyVariant copyVariant = copyVariantId != null
                                ? copyVariantRepository.findById(copyVariantId).orElse(null)
                                : null;
                        String primaryText = firstNonBlank(
                                ad.getPrimaryText(),
                                copyVariant != null ? copyVariant.getPrimaryText() : null);
                        String headline = firstNonBlank(
                                ad.getHeadline(),
                                copyVariant != null ? copyVariant.getHeadline() : null,
                                ad.getName());
                        String description = firstNonBlank(
                                ad.getDescription(),
                                copyVariant != null ? copyVariant.getDescription() : null);
                        String cta = firstNonBlank(
                                ad.getCta(),
                                copyVariant != null ? copyVariant.getCta() : null,
                                "LEARN_MORE");
                        String destinationUrl = firstNonBlank(
                                ad.getDestinationUrl(),
                                emailProperties.getBaseUrl());

                        // 3b. Upload image to Meta (if we have a creative asset)
                        String imageHash = null;
                        if (creativeAssetId != null) {
                            CreativeAsset asset = creativeAssetRepository.findById(creativeAssetId)
                                    .orElse(null);
                            if (asset != null && "IMAGE".equals(asset.getAssetType())) {
                                try {
                                    byte[] imageBytes = s3StorageService.downloadFile(asset.getS3Key());
                                    if (imageBytes != null && imageBytes.length > 0) {
                                        JsonNode uploadResult = metaClient.uploadImage(
                                                accessToken, adAccountId,
                                                imageBytes, asset.getOriginalFilename());
                                        imageHash = parseImageHash(uploadResult);
                                        steps.add(Map.of("step", "upload", "status", "done",
                                                "message", "Uploaded image for ad " + ad.getName()));
                                        log.info("Uploaded image for ad '{}', hash: {}", ad.getName(), imageHash);
                                    } else {
                                        log.warn("Image bytes empty for asset {}, creating ad without image", creativeAssetId);
                                        steps.add(Map.of("step", "upload", "status", "warning",
                                                "message", "Image empty for ad " + ad.getName() + ", continuing without image"));
                                    }
                                } catch (Exception imgEx) {
                                    log.warn("Image upload failed for ad '{}': {}, continuing without image",
                                            ad.getName(), imgEx.getMessage());
                                    steps.add(Map.of("step", "upload", "status", "warning",
                                            "message", "Image upload failed for ad " + ad.getName() + ": " + imgEx.getMessage()));
                                }
                            }
                        }

                        // 3c. Create ad creative in Meta
                        JsonNode adCreative = metaClient.createAdCreative(
                                accessToken, adAccountId,
                                ad.getName() + " Creative",
                                imageHash, pageId,
                                primaryText, headline, description, cta, destinationUrl);
                        String creativeId = adCreative.get("id").asText();
                        log.info("Created Meta ad creative: {}", creativeId);

                        // 3d. Create the ad in Meta referencing the creative
                        JsonNode metaAd = metaClient.createAd(
                                accessToken, metaAdsetId,
                                ad.getName(), creativeId, "PAUSED");
                        String metaAdId = metaAd.get("id").asText();
                        ad.setMetaAdId(metaAdId);
                        adRepo.save(ad);
                        totalAdsCreated++;
                        saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
                                "AD_CREATE",
                                objectMapper.createObjectNode()
                                        .put("name", ad.getName())
                                        .put("creative_id", creativeId)
                                        .put("image_hash", imageHash),
                                metaAd, true,
                                objectMapper.createObjectNode()
                                        .put("ad_id", ad.getId().toString())
                                        .put("meta_ad_id", metaAdId));
                        steps.add(Map.of("step", "ad", "status", "done",
                                "message", "Created ad " + adIdx + " of " + adsForAdset.size()
                                        + " in adset " + adsetIdx));
                        log.info("Created Meta ad: {}", metaAdId);

                    } catch (Exception adEx) {
                        // Ad creation failed — log and continue with other ads
                        log.error("Failed to create ad '{}': {}", ad.getName(), adEx.getMessage());
                        saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
                                "AD_CREATE_FAILED",
                                objectMapper.createObjectNode().put("ad_name", ad.getName()),
                                objectMapper.createObjectNode().put("error", adEx.getMessage()),
                                false, null);
                        steps.add(Map.of("step", "ad", "status", "error",
                                "message", "Failed ad '" + ad.getName() + "': " + adEx.getMessage()));
                    }
                }
            }

            // ── Step 4: Activate everything ──
            metaClient.updateCampaignStatus(accessToken, metaCampaignId, "ACTIVE");
            steps.add(Map.of("step", "activate", "status", "done",
                    "message", "Campaign activated"));
            saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
                    "CAMPAIGN_ACTIVATE",
                    objectMapper.createObjectNode().put("meta_campaign_id", metaCampaignId),
                    objectMapper.createObjectNode().put("status", "ACTIVE"),
                    true, null);

            for (Adset adset : adsets) {
                if (adset.getMetaAdsetId() != null) {
                    try {
                        metaClient.updateAdsetStatus(accessToken, adset.getMetaAdsetId(), "ACTIVE");
                        adset.setStatus("ACTIVE");
                        adsetRepo.save(adset);
                    } catch (Exception e) {
                        log.warn("Failed to activate adset {}: {}", adset.getMetaAdsetId(), e.getMessage());
                    }
                }
            }

            List<Ad> allAds = new ArrayList<>();
            for (Adset adset : adsets) {
                allAds.addAll(adRepo.findAllByAdsetId(adset.getId()));
            }
            for (Ad ad : allAds) {
                if (ad.getMetaAdId() != null) {
                    try {
                        metaClient.updateAdStatus(accessToken, ad.getMetaAdId(), "ACTIVE");
                        ad.setStatus("ACTIVE");
                        adRepo.save(ad);
                    } catch (Exception e) {
                        log.warn("Failed to activate ad {}: {}", ad.getMetaAdId(), e.getMessage());
                    }
                }
            }

            // ── Step 5: Mark campaign as PUBLISHED ──
            campaign.setStatus("PUBLISHED");
            campaignRepo.save(campaign);
            saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
                    "CAMPAIGN_PUBLISH_COMPLETE",
                    objectMapper.createObjectNode().put("campaign_id", campaignId.toString()),
                    objectMapper.createObjectNode().put("status", "PUBLISHED"),
                    true,
                    objectMapper.createObjectNode()
                            .put("meta_campaign_id", metaCampaignId)
                            .put("status", "PUBLISHED"));
            steps.add(Map.of("step", "complete", "status", "done",
                    "message", "Campaign published successfully"));

            // Audit
            TenantContext ctx = TenantContextHolder.get();
            if (ctx != null) {
                auditService.log(agencyId, campaign.getClientId(),
                        ctx.getUserId(), ctx.getRole(),
                        AuditAction.CAMPAIGN_PUBLISH, "Campaign", campaignId,
                        "DRAFT", "PUBLISHED", null);
            }

            log.info("Successfully published campaign {} to Meta", campaignId);

            // Send notification
            sendPublishNotification(agencyId, campaign);

            return Map.of(
                    "status", "PUBLISHED",
                    "campaignId", campaignId,
                    "metaCampaignId", metaCampaignId,
                    "adsetsCreated", adsets.size(),
                    "adsCreated", totalAdsCreated,
                    "steps", steps);

        } catch (Exception e) {
            log.error("Failed to publish campaign {}: {}", campaignId, e.getMessage(), e);

            // If we created a campaign in Meta, try to delete/pause it
            if (metaCampaignId != null) {
                tryDeleteMetaCampaign(accessToken, metaCampaignId);
            }

            markCampaignFailed(agencyId, campaign, campaignId, e.getMessage());
            steps.add(Map.of("step", "error", "status", "error",
                    "message", e.getMessage()));

            return Map.of(
                    "status", "FAILED",
                    "campaignId", campaignId,
                    "error", e.getMessage(),
                    "steps", steps);
        }
    }

    // ──────── Publish helpers ────────

    /**
     * Parse the image hash from Meta's adimages upload response.
     * Response format: {"images":{"filename":{"hash":"abc123",...}}}
     */
    private String parseImageHash(JsonNode uploadResult) {
        if (uploadResult == null) return null;
        JsonNode images = uploadResult.get("images");
        if (images != null && images.isObject()) {
            // The key under "images" is the filename — just get the first entry
            var fields = images.fields();
            if (fields.hasNext()) {
                JsonNode imageInfo = fields.next().getValue();
                if (imageInfo.has("hash")) {
                    return imageInfo.get("hash").asText();
                }
            }
        }
        log.warn("Could not parse image hash from response: {}", uploadResult);
        return null;
    }

    /**
     * Best-effort deletion of a Meta campaign (cleanup on failure).
     */
    private void tryDeleteMetaCampaign(String accessToken, String metaCampaignId) {
        try {
            metaClient.updateCampaignStatus(accessToken, metaCampaignId, "PAUSED");
            log.info("Paused orphaned Meta campaign {} after publish failure", metaCampaignId);
        } catch (Exception cleanupEx) {
            log.warn("Failed to pause orphaned Meta campaign {}: {}", metaCampaignId, cleanupEx.getMessage());
        }
    }

    /**
     * Mark campaign as FAILED and log.
     */
    private void markCampaignFailed(UUID agencyId, Campaign campaign, UUID campaignId, String errorMsg) {
        campaign.setStatus("FAILED");
        campaignRepo.save(campaign);
        saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
                "CAMPAIGN_PUBLISH_FAILED",
                objectMapper.createObjectNode().put("campaign_id", campaignId.toString()),
                objectMapper.createObjectNode().put("error", errorMsg),
                false,
                objectMapper.createObjectNode().put("status", "FAILED"));
    }

    /**
     * Send publish notification emails.
     */
    private void sendPublishNotification(UUID agencyId, Campaign campaign) {
        try {
            String clientName = clientRepo.findByIdAndAgencyId(campaign.getClientId(), agencyId)
                    .map(Client::getName).orElse("Client");
            String dashboardLink = emailProperties.getBaseUrl() + "/campaigns";
            List<String> recipients = notificationHelper.getAssignedUserEmails(agencyId, campaign.getClientId());
            for (String email : recipients) {
                notificationHelper.sendTemplatedAsync(email,
                        "Campaign Published — " + campaign.getName(),
                        "campaign-published",
                        Map.of(
                                "campaignName", campaign.getName(),
                                "clientName", clientName,
                                "dashboardLink", dashboardLink
                        ));
            }
            log.info("Queued campaign-published notification to {} recipient(s)", recipients.size());
        } catch (Exception notifEx) {
            log.warn("Failed to send campaign-published notification: {}", notifEx.getMessage());
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
