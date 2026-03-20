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
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
        ZoneId clientZone = resolveClientZoneId(agencyId, campaign.getClientId());

        List<Adset> adsets = adsetRepo.findAllByCampaignId(campaignId);
        List<Map<String, String>> steps = new ArrayList<>();

        String metaCampaignId = null;

        try {
            // ── Step 1: Create campaign in Meta as PAUSED ──
            log.info("Publishing campaign '{}' to Meta account {}", campaign.getName(), adAccountId);
            String budgetType = "CBO".equalsIgnoreCase(campaign.getBudgetType()) ? "CBO" : "ABO";
                String validatedObjective = validateObjective(campaign.getObjective());
                if (!Objects.equals(validatedObjective, campaign.getObjective())) {
                log.warn("Normalized campaign objective '{}' to '{}' before Meta publish for campaign {}",
                    campaign.getObjective(), validatedObjective, campaignId);
                campaign.setObjective(validatedObjective);
                campaignRepo.save(campaign);
                }
            JsonNode metaCampaign = metaClient.createCampaign(
                    accessToken, adAccountId,
                    campaign.getName(), validatedObjective, "PAUSED",
                    budgetType, campaign.getDailyBudget());
            metaCampaignId = metaCampaign.get("id").asText();
            campaign.setMetaCampaignId(metaCampaignId);
            campaignRepo.save(campaign);
            com.fasterxml.jackson.databind.node.ObjectNode campaignRequest = objectMapper.createObjectNode()
                    .put("name", campaign.getName())
                    .put("objective", validatedObjective)
                    .put("budget_type", budgetType);
            if (campaign.getDailyBudget() != null) {
                campaignRequest.put("daily_budget", campaign.getDailyBudget());
            }
            saveCampaignActionLog(agencyId, campaign.getClientId(), campaignId,
                    "CAMPAIGN_CREATE",
                    campaignRequest,
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
                boolean isCBO = "CBO".equals(budgetType);
                Long budgetCents = !isCBO && adset.getDailyBudget() != null
                        ? adset.getDailyBudget().multiply(java.math.BigDecimal.valueOf(100)).longValue()
                        : null;

                // Transform targeting JSON to Meta-compatible format
                String metaTargetingJson;
                try {
                    metaTargetingJson = buildMetaTargeting(adset.getTargetingJson());
                } catch (Exception tgtEx) {
                    log.error("Failed to build Meta targeting for adset '{}': {}", adset.getName(), tgtEx.getMessage());
                    markCampaignFailed(agencyId, campaign, campaignId,
                            "Invalid targeting for adset '" + adset.getName() + "': " + tgtEx.getMessage());
                    return Map.of(
                            "status", "FAILED",
                            "campaignId", campaignId,
                            "error", "Invalid targeting: " + tgtEx.getMessage(),
                            "failedStep", "adset_targeting_" + adsetIdx,
                            "steps", steps);
                }

                log.info("Creating adset {}/{} '{}' — isCBO={}, budgetCents={}", adsetIdx, adsets.size(),
                        adset.getName(), isCBO, budgetCents);

                String promotedObjectJson;
                try {
                    promotedObjectJson = buildPromotedObject(conn, adset);
                } catch (Exception promotedObjectEx) {
                    log.error("Failed to build promoted_object for adset '{}': {}", adset.getName(), promotedObjectEx.getMessage());
                    markCampaignFailed(agencyId, campaign, campaignId,
                        "Failed to create adset '" + adset.getName() + "': " + promotedObjectEx.getMessage());
                    return Map.of(
                        "status", "FAILED",
                        "campaignId", campaignId,
                        "error", promotedObjectEx.getMessage(),
                        "failedStep", "adset_promoted_object_" + adsetIdx,
                        "steps", steps);
                }

                String startTimeUnix = resolveAdsetStartTime(adset, clientZone);
                String endTimeUnix = resolveAdsetEndTime(adset, clientZone);

                JsonNode metaAdset;
                try {
                    metaAdset = metaClient.createAdset(
                        accessToken, adAccountId, metaCampaignId,
                            adset.getName(), budgetCents,
                            metaTargetingJson,
                            adset.getOptimizationGoal() != null
                                    ? adset.getOptimizationGoal() : "LINK_CLICKS",
                                promotedObjectJson,
                        startTimeUnix,
                        endTimeUnix,
                            "PAUSED", isCBO);
                } catch (Exception adsetEx) {
                    log.error("Failed to create adset '{}' in Meta: {}", adset.getName(), adsetEx.getMessage());
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
                    buildAdsetRequestLog(adset, budgetCents, budgetType, promotedObjectJson, startTimeUnix, endTimeUnix, clientZone),
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
                                        imageHash = metaClient.uploadImage(
                                                accessToken, adAccountId,
                                                imageBytes, asset.getOriginalFilename());
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
                            accessToken, adAccountId, metaAdsetId,
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

            // Don't try to rollback Meta objects — let them stay PAUSED.
            // User can delete them manually from Meta Ads Manager.

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
     * Transform our internal targeting JSON to Meta's expected format.
     * Meta REQUIRES at minimum: {"geo_locations":{"countries":["XX"]}}
     */
    private String buildMetaTargeting(String ourTargetingJson) {
        try {
            ObjectNode metaTargeting = objectMapper.createObjectNode();
            JsonNode our = objectMapper.readTree(ourTargetingJson);

            // Geo locations (REQUIRED)
            ObjectNode geoLocations = metaTargeting.putObject("geo_locations");
            if (our.has("locations") && our.get("locations").isArray() && our.get("locations").size() > 0) {
                ArrayNode countries = null;
                ArrayNode cities = null;
                ArrayNode regions = null;

                for (JsonNode location : our.get("locations")) {
                    String type = location.path("type").asText("country").toLowerCase();
                    String key = location.path("key").asText();
                    String countryCode = location.path("country_code").asText();

                    switch (type) {
                        case "country" -> {
                            if (countries == null) {
                                countries = geoLocations.putArray("countries");
                            }
                            String countryValue = !countryCode.isBlank() ? countryCode : key;
                            if (!countryValue.isBlank()) {
                                countries.add(countryValue);
                            }
                        }
                        case "city" -> {
                            if (cities == null) {
                                cities = geoLocations.putArray("cities");
                            }
                            if (!key.isBlank()) {
                                cities.addObject().put("key", key);
                            }
                        }
                        case "region" -> {
                            if (regions == null) {
                                regions = geoLocations.putArray("regions");
                            }
                            if (!key.isBlank()) {
                                regions.addObject().put("key", key);
                            }
                        }
                        default -> log.warn("Unsupported location type '{}' in targeting payload", type);
                    }
                }
            } else if (our.has("geoLocations") && our.get("geoLocations").has("countries")) {
                ArrayNode countries = geoLocations.putArray("countries");
                for (JsonNode c : our.get("geoLocations").get("countries")) {
                    countries.add(c.asText());
                }
            } else if (our.has("geo_locations")) {
                // Already in Meta format
                metaTargeting.set("geo_locations", our.get("geo_locations"));
            } else {
                // Fallback — MUST have at least one country
                ArrayNode countries = geoLocations.putArray("countries");
                countries.add("BG"); // default
                log.warn("No geo_locations found in targeting, defaulting to BG");
            }

            // Age
            if (our.has("ageRange") && our.get("ageRange").isArray() && our.get("ageRange").size() >= 2) {
                metaTargeting.put("age_min", our.get("ageRange").get(0).asInt(18));
                metaTargeting.put("age_max", our.get("ageRange").get(1).asInt(65));
            } else {
                if (our.has("age_min") || our.has("ageMin")) {
                metaTargeting.put("age_min", our.has("age_min") ? our.get("age_min").asInt() : our.get("ageMin").asInt(18));
                }
                if (our.has("age_max") || our.has("ageMax")) {
                metaTargeting.put("age_max", our.has("age_max") ? our.get("age_max").asInt() : our.get("ageMax").asInt(65));
                }
            }

            // Genders (1=male, 2=female, empty=all)
            if (our.has("genders") && our.get("genders").isArray() && our.get("genders").size() > 0) {
                metaTargeting.set("genders", our.get("genders"));
            } else if (our.has("gender")) {
                String gender = our.get("gender").asText("all");
                if ("male".equalsIgnoreCase(gender)) {
                    ArrayNode genders = metaTargeting.putArray("genders");
                    genders.add(1);
                } else if ("female".equalsIgnoreCase(gender)) {
                    ArrayNode genders = metaTargeting.putArray("genders");
                    genders.add(2);
                }
            }

            // Interests → flexible_spec
            if (our.has("interests") && our.get("interests").isArray() && our.get("interests").size() > 0) {
                ArrayNode flexSpec = metaTargeting.putArray("flexible_spec");
                ObjectNode spec = flexSpec.addObject();
                ArrayNode interests = spec.putArray("interests");
                for (JsonNode interest : our.get("interests")) {
                    ObjectNode i = interests.addObject();
                    i.put("id", interest.has("id") ? interest.get("id").asText() : interest.asText());
                    if (interest.has("name")) i.put("name", interest.get("name").asText());
                }
            }

            // Custom audiences
            if (our.has("customAudiences") && our.get("customAudiences").isArray() && our.get("customAudiences").size() > 0) {
                ArrayNode customAudiences = metaTargeting.putArray("custom_audiences");
                for (JsonNode ca : our.get("customAudiences")) {
                    ObjectNode aud = customAudiences.addObject();
                    aud.put("id", ca.has("id") ? ca.get("id").asText() : ca.asText());
                }
            } else if (our.has("custom_audiences") && our.get("custom_audiences").isArray() && our.get("custom_audiences").size() > 0) {
                metaTargeting.set("custom_audiences", our.get("custom_audiences"));
            }

            // Excluded audiences
            if (our.has("excludedAudiences") && our.get("excludedAudiences").isArray() && our.get("excludedAudiences").size() > 0) {
                ObjectNode exclusions = metaTargeting.putObject("exclusions");
                ArrayNode excCustom = exclusions.putArray("custom_audiences");
                for (JsonNode ea : our.get("excludedAudiences")) {
                    ObjectNode aud = excCustom.addObject();
                    aud.put("id", ea.has("id") ? ea.get("id").asText() : ea.asText());
                }
            } else if (our.has("excluded_custom_audiences") && our.get("excluded_custom_audiences").isArray() && our.get("excluded_custom_audiences").size() > 0) {
                ObjectNode exclusions = metaTargeting.putObject("exclusions");
                exclusions.set("custom_audiences", our.get("excluded_custom_audiences"));
            }

            String result = metaTargeting.toString();
            log.debug("Targeting transformed: {} → {}", ourTargetingJson, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to transform targeting JSON: {}", e.getMessage());
            throw new IllegalStateException("Failed to build Meta targeting: " + e.getMessage(), e);
        }
    }

    private String validateObjective(String objective) {
        String normalized = objective == null ? "" : objective.trim().toUpperCase(Locale.ROOT);
        Set<String> valid = Set.of(
                "OUTCOME_SALES", "OUTCOME_LEADS", "OUTCOME_TRAFFIC",
                "OUTCOME_AWARENESS", "OUTCOME_ENGAGEMENT", "OUTCOME_APP_PROMOTION"
        );

        if (valid.contains(normalized)) {
            return normalized;
        }

        return switch (normalized) {
            case "SALES", "CONVERSIONS", "PURCHASE" -> "OUTCOME_SALES";
            case "LEADS", "LEAD_GENERATION" -> "OUTCOME_LEADS";
            case "TRAFFIC", "LINK_CLICKS" -> "OUTCOME_TRAFFIC";
            case "AWARENESS", "BRAND_AWARENESS", "REACH" -> "OUTCOME_AWARENESS";
            case "ENGAGEMENT", "POST_ENGAGEMENT", "VIDEO_VIEWS" -> "OUTCOME_ENGAGEMENT";
            case "APP_INSTALLS", "APP_PROMOTION" -> "OUTCOME_APP_PROMOTION";
            default -> "OUTCOME_SALES";
        };
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
        actionLog.setSuggestionId(null);
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

    private com.fasterxml.jackson.databind.node.ObjectNode buildAdsetRequestLog(Adset adset, Long budgetCents, String budgetType,
                                                                                String promotedObjectJson,
                                                                                String startTimeUnix, String endTimeUnix,
                                                                                ZoneId clientZone) {
        com.fasterxml.jackson.databind.node.ObjectNode requestNode = objectMapper.createObjectNode()
                .put("name", adset.getName())
                .put("budget_type", budgetType)
                .put("timezone", clientZone.getId())
                .put("start_time", startTimeUnix)
                .put("optimization_goal", adset.getOptimizationGoal())
                .put("conversion_event", firstNonBlank(adset.getConversionEvent(), ""));
        if (budgetCents != null) {
            requestNode.put("daily_budget", budgetCents);
        }
        if (endTimeUnix != null) {
            requestNode.put("end_time", endTimeUnix);
        }
        if (promotedObjectJson != null) {
            requestNode.put("promoted_object", promotedObjectJson);
        }
        return requestNode;
    }

    private String buildPromotedObject(MetaConnection conn, Adset adset) {
        if (!"OFFSITE_CONVERSIONS".equalsIgnoreCase(adset.getOptimizationGoal())) {
            return null;
        }

        String pixelId = conn.getPixelId();
        if (pixelId == null || pixelId.isBlank()) {
            throw new IllegalStateException("Pixel ID is required for conversion campaigns. Please reconnect Meta with Pixel access.");
        }

        ObjectNode promotedObject = objectMapper.createObjectNode();
        promotedObject.put("pixel_id", pixelId);
        promotedObject.put("custom_event_type", firstNonBlank(adset.getConversionEvent(), "PURCHASE"));
        return promotedObject.toString();
    }

    public String buildMetaTargetingForUpdate(String targetingJson) {
        return buildMetaTargeting(targetingJson);
    }

    public String buildPromotedObjectForUpdate(MetaConnection conn, Adset adset) {
        return buildPromotedObject(conn, adset);
    }

    private ZoneId resolveClientZoneId(UUID agencyId, UUID clientId) {
        String timezone = clientRepo.findByIdAndAgencyId(clientId, agencyId)
                .map(Client::getTimezone)
                .filter(value -> value != null && !value.isBlank())
                .orElse("Europe/Sofia");
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            log.warn("Invalid client timezone '{}' for client {}, falling back to Europe/Sofia", timezone, clientId);
            return ZoneId.of("Europe/Sofia");
        }
    }

    private String resolveAdsetStartTime(Adset adset, ZoneId zoneId) {
        LocalDate startDate = adset.getStartDate() != null
                ? adset.getStartDate()
                : LocalDate.now(zoneId).plusDays(1);
        return String.valueOf(startDate.atStartOfDay(zoneId).toInstant().getEpochSecond());
    }

    private String resolveAdsetEndTime(Adset adset, ZoneId zoneId) {
        if (adset.getEndDate() == null) {
            return null;
        }
        return String.valueOf(adset.getEndDate()
                .atTime(23, 59, 59)
                .atZone(zoneId)
                .toInstant()
                .getEpochSecond());
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
