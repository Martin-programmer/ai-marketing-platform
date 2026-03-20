package com.amp.ai;

import com.amp.campaigns.*;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.creatives.CopyVariant;
import com.amp.creatives.CopyVariantRepository;
import com.amp.creatives.CreativeAsset;
import com.amp.creatives.CreativeAssetRepository;
import com.amp.creatives.CreativePackageItem;
import com.amp.creatives.CreativePackageItemRepository;
import com.amp.creatives.CreativePackageRepository;
import com.amp.creatives.CreativeService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.amp.meta.MetaConnection;
import com.amp.meta.MetaConnectionRepository;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Generates full campaign proposals using Claude.
 * <p>
 * Gathers client context (profile, creatives, copy variants, historical insights,
 * active campaigns, Meta connection details), sends it to Claude with a structured
 * system prompt, parses the JSON response, and persists DRAFT campaign + adsets + ads.
 */
@Service
public class CampaignCreatorService {

    private static final Logger log = LoggerFactory.getLogger(CampaignCreatorService.class);

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;
    private final ClientRepository clientRepo;
    private final CreativeAssetRepository creativeRepo;
    private final CopyVariantRepository copyRepo;
    private final CreativePackageRepository creativePackageRepository;
    private final CreativePackageItemRepository creativePackageItemRepository;
    private final CreativeService creativeService;
    private final InsightDailyRepository insightRepo;
    private final CampaignRepository campaignRepo;
    private final AdsetRepository adsetRepo;
    private final AdRepository adRepo;
    private final MetaConnectionRepository metaConnRepo;
    private final AiContextBuilder aiContextBuilder;
    private final AiCrossModuleSupportService aiCrossModuleSupportService;
    private final ObjectMapper objectMapper;

    public CampaignCreatorService(ClaudeApiClient claudeClient,
                                  AiProperties aiProps,
                                  ClientRepository clientRepo,
                                  CreativeAssetRepository creativeRepo,
                                  CopyVariantRepository copyRepo,
                                  CreativePackageRepository creativePackageRepository,
                                  CreativePackageItemRepository creativePackageItemRepository,
                                  CreativeService creativeService,
                                  InsightDailyRepository insightRepo,
                                  CampaignRepository campaignRepo,
                                  AdsetRepository adsetRepo,
                                  AdRepository adRepo,
                                  MetaConnectionRepository metaConnRepo,
                                  AiContextBuilder aiContextBuilder,
                                  AiCrossModuleSupportService aiCrossModuleSupportService,
                                  ObjectMapper objectMapper) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.clientRepo = clientRepo;
        this.creativeRepo = creativeRepo;
        this.copyRepo = copyRepo;
        this.creativePackageRepository = creativePackageRepository;
        this.creativePackageItemRepository = creativePackageItemRepository;
        this.creativeService = creativeService;
        this.insightRepo = insightRepo;
        this.campaignRepo = campaignRepo;
        this.adsetRepo = adsetRepo;
        this.adRepo = adRepo;
        this.metaConnRepo = metaConnRepo;
        this.aiContextBuilder = aiContextBuilder;
        this.aiCrossModuleSupportService = aiCrossModuleSupportService;
        this.objectMapper = objectMapper;
    }

    // ──────── Public API ────────

    /**
     * Generate a full AI campaign proposal for a client.
     */
    @Transactional
    public CampaignProposalResponse generateProposal(UUID agencyId, UUID clientId, String briefText,
                                                     String requestedBudgetType, BigDecimal requestedCampaignDailyBudget) {
        TenantContext ctx = TenantContextHolder.require();

        Client client = clientRepo.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        // ── 1. Gather context ──
        String contextPayload = buildContextPayload(agencyId, clientId, briefText,
                requestedBudgetType, requestedCampaignDailyBudget);

        // ── 2. Call Claude Sonnet ──
        String systemPrompt = buildSystemPrompt();
        String model = aiProps.getAnthropic().getDefaultModel();
        int maxTokens = 8192;

        ClaudeApiClient.ClaudeResponse response = claudeClient.sendMessage(
                systemPrompt, contextPayload, "CAMPAIGN_CREATOR",
                agencyId, clientId, model, maxTokens);

        if (!response.isSuccess()) {
            throw new IllegalStateException("AI proposal generation failed: " + response.error());
        }

        // ── 3. Parse JSON response ──
        JsonNode proposalJson = claudeClient.parseJson(response.text());
        if (proposalJson == null) {
            throw new IllegalStateException("Failed to parse AI response as JSON");
        }

        // ── 4. Deduplicate by campaign name ──
        String campaignName = proposalJson.has("campaign_name")
                ? proposalJson.get("campaign_name").asText()
                : "AI Campaign — " + client.getName();

        boolean exists = campaignRepo.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream().anyMatch(c -> campaignName.equals(c.getName()));
        if (exists) {
            throw new IllegalStateException(
                    "A campaign named '" + campaignName + "' already exists for this client");
        }

        // ── 5. Persist DRAFT campaign structure ──
        return persistProposal(agencyId, clientId, ctx.getUserId(), proposalJson, campaignName,
                requestedBudgetType, requestedCampaignDailyBudget);
    }

    // ──────── Context builder ────────

    private String buildContextPayload(UUID agencyId, UUID clientId, String briefText,
                                       String requestedBudgetType, BigDecimal requestedCampaignDailyBudget) {
        CompletableFuture<String> sharedContextFuture = CompletableFuture.supplyAsync(
            () -> aiContextBuilder.buildContext(agencyId, clientId));
        CompletableFuture<String> crossModuleContextFuture =
            aiCrossModuleSupportService.buildCampaignCreatorSectionsAsync(agencyId, clientId);

        StringBuilder sb = new StringBuilder();
        sb.append(sharedContextFuture.join()).append("\n");
        String crossModuleContext = crossModuleContextFuture.join();
        if (crossModuleContext != null && !crossModuleContext.isBlank()) {
            sb.append(crossModuleContext);
        }

        // Agency brief
        if (briefText != null && !briefText.isBlank()) {
            sb.append("Agency Brief:\n").append(briefText).append("\n\n");
        }

        String budgetType = normalizeBudgetType(requestedBudgetType);
        sb.append("Budget Preferences:\n")
                .append("- Budget type: ").append(budgetType).append("\n");
        if ("CBO".equals(budgetType) && requestedCampaignDailyBudget != null) {
            sb.append("- Campaign daily budget: ").append(requestedCampaignDailyBudget).append("\n");
        }
        sb.append("\n");

        return sb.toString();
    }

    // ──────── System prompt ────────

    private String buildSystemPrompt() {
        return """
            You are a senior Meta Ads media buyer with 10+ years of experience.
            Create a detailed campaign proposal in STRICT JSON format.

            The JSON must have this exact structure:
            {
              "campaign_name": "string",
                            "objective": "OUTCOME_SALES|OUTCOME_LEADS|OUTCOME_TRAFFIC|OUTCOME_AWARENESS|OUTCOME_ENGAGEMENT|OUTCOME_APP_PROMOTION",
              "budget_type": "ABO|CBO",
              "campaign_daily_budget": number or null,
              "rationale": "string explaining your strategic reasoning",
              "suggested_daily_budget": number,
              "estimated_results": "string with expected KPIs",
              "warnings": ["array of warnings or caveats"],
              "adsets": [
                {
                  "name": "string",
                  "daily_budget": number,
                  "targeting": {"age_min": 25, "age_max": 55, "genders": [1,2], "interests": ["..."]},
                  "optimization_goal": "CONVERSIONS|LINK_CLICKS|IMPRESSIONS|REACH",
                  "ads": [
                    {
                      "name": "string",
                      "creative_asset_id": "UUID or null",
                      "primary_text": "string (max 125 chars for best performance)",
                      "headline": "string (max 40 chars)",
                      "description": "string (max 30 chars)",
                      "cta": "LEARN_MORE|SHOP_NOW|SIGN_UP|GET_OFFER|CONTACT_US",
                      "url": "https://..."
                    }
                  ]
                }
              ]
            }

            Rules:
            - Use ONLY these exact Meta objective values: OUTCOME_SALES, OUTCOME_LEADS, OUTCOME_TRAFFIC, OUTCOME_AWARENESS, OUTCOME_ENGAGEMENT, OUTCOME_APP_PROMOTION
            - Use OUTCOME_SALES for purchase or conversion campaigns
            - Use OUTCOME_LEADS for lead generation campaigns
            - Use OUTCOME_TRAFFIC for website traffic campaigns
            - Use OUTCOME_AWARENESS for brand awareness or reach campaigns
            - Use OUTCOME_ENGAGEMENT for post engagement or video view campaigns
            - Use OUTCOME_APP_PROMOTION for app install or app promotion campaigns
            - Reference existing creative_asset_id UUIDs from the provided list when available
            - Reuse approved copy variants from the provided context when possible instead of inventing new ad text
            - If no creatives are available, set creative_asset_id to null and add a warning
            - Respect the requested budget type from the context
            - If budget_type is CBO, use campaign_daily_budget for the main budget and still create multiple adsets
            - Create 2-4 adsets with different targeting segments
            - Each adset should have 2-3 ads (for A/B testing)
            - Budget allocation should be strategic across adsets
            - Consider historical performance when setting expectations
            - Campaign name must be unique and descriptive
            - Respond ONLY with the JSON object, no markdown or extra text
            """;
    }

    // ──────── Persist proposal ────────

    private CampaignProposalResponse persistProposal(UUID agencyId, UUID clientId, UUID userId,
                                                     JsonNode json, String campaignName,
                                                     String requestedBudgetType, BigDecimal requestedCampaignDailyBudget) {
        String objective = validateObjective(json.has("objective") ? json.get("objective").asText() : "OUTCOME_SALES");
        String budgetType = normalizeBudgetType(json.has("budget_type")
                ? json.get("budget_type").asText()
                : requestedBudgetType);
        String rationale = json.has("rationale") ? json.get("rationale").asText() : "";
        BigDecimal suggestedBudget = json.has("suggested_daily_budget")
                ? BigDecimal.valueOf(json.get("suggested_daily_budget").asDouble())
                : BigDecimal.ZERO;
        BigDecimal campaignDailyBudget = "CBO".equals(budgetType)
                ? firstPositive(
                        json.has("campaign_daily_budget") && !json.get("campaign_daily_budget").isNull()
                                ? BigDecimal.valueOf(json.get("campaign_daily_budget").asDouble()) : null,
                        requestedCampaignDailyBudget,
                        suggestedBudget)
                : null;
        String estimatedResults = json.has("estimated_results")
                ? json.get("estimated_results").asText() : "";

        List<String> warnings = new ArrayList<>();
        if (json.has("warnings") && json.get("warnings").isArray()) {
            for (JsonNode w : json.get("warnings")) {
                warnings.add(w.asText());
            }
        }

        List<CreativePackageItem> approvedPackageItems = creativeService.listApprovedPackageItems(agencyId, clientId, objective);
        if (!approvedPackageItems.isEmpty()) {
            warnings.add("Using approved creative package items for ad creatives.");
        }

        // Create campaign
        Campaign campaign = new Campaign();
        campaign.setAgencyId(agencyId);
        campaign.setClientId(clientId);
        campaign.setPlatform("META");
        campaign.setName(campaignName);
        campaign.setObjective(objective);
        campaign.setBudgetType(budgetType);
        campaign.setDailyBudget(campaignDailyBudget);
        campaign.setStatus("DRAFT");
        campaign.setCreatedBy(userId);
        campaign.setCreatedAt(OffsetDateTime.now());
        campaign.setUpdatedAt(OffsetDateTime.now());
        campaign = campaignRepo.save(campaign);

        // Create adsets + ads
        List<CampaignProposalResponse.ProposedAdset> proposedAdsets = new ArrayList<>();

        JsonNode adsetsJson = json.get("adsets");
        if (adsetsJson != null && adsetsJson.isArray()) {
            if ("CBO".equals(budgetType) && adsetsJson.size() < 2) {
                warnings.add("CBO works best with 2+ ad sets.");
            }
            for (JsonNode adsetJson : adsetsJson) {
                Adset adset = new Adset();
                adset.setAgencyId(agencyId);
                adset.setClientId(clientId);
                adset.setCampaignId(campaign.getId());
                adset.setName(adsetJson.has("name") ? adsetJson.get("name").asText() : "Adset");
                adset.setDailyBudget("CBO".equals(budgetType)
                        ? BigDecimal.ZERO
                        : adsetJson.has("daily_budget")
                            ? BigDecimal.valueOf(adsetJson.get("daily_budget").asDouble())
                            : BigDecimal.TEN);
                adset.setTargetingJson(adsetJson.has("targeting")
                        ? adsetJson.get("targeting").toString() : "{}");
                adset.setOptimizationGoal(adsetJson.has("optimization_goal")
                    ? adsetJson.get("optimization_goal").asText()
                    : "CONVERSIONS");
                adset.setStatus("DRAFT");
                adset.setCreatedAt(OffsetDateTime.now());
                adset.setUpdatedAt(OffsetDateTime.now());
                adset = adsetRepo.save(adset);

                String optGoal = adset.getOptimizationGoal();

                // Create ads
                List<CampaignProposalResponse.ProposedAd> proposedAds = new ArrayList<>();
                if (!approvedPackageItems.isEmpty()) {
                    for (CreativePackageItem packageItem : approvedPackageItems) {
                        CopyVariant copyVariant = copyRepo.findById(packageItem.getCopyVariantId()).orElse(null);
                        Ad ad = new Ad();
                        ad.setAgencyId(agencyId);
                        ad.setClientId(clientId);
                        ad.setAdsetId(adset.getId());
                        ad.setName(buildPackageAdName(copyVariant, packageItem));
                        ad.setCreativePackageItemId(packageItem.getId());
                        ad.setCreativeAssetId(packageItem.getCreativeAssetId());
                        ad.setCopyVariantId(packageItem.getCopyVariantId());
                        ad.setPrimaryText(copyVariant != null ? copyVariant.getPrimaryText() : null);
                        ad.setHeadline(copyVariant != null ? copyVariant.getHeadline() : null);
                        ad.setDescription(copyVariant != null ? copyVariant.getDescription() : null);
                        ad.setCta(firstNonBlank(packageItem.getCtaType(), copyVariant != null ? copyVariant.getCta() : null, "LEARN_MORE"));
                        ad.setDestinationUrl(packageItem.getDestinationUrl());
                        ad.setStatus("DRAFT");
                        ad.setCreatedAt(OffsetDateTime.now());
                        ad.setUpdatedAt(OffsetDateTime.now());
                        ad = adRepo.save(ad);

                        proposedAds.add(new CampaignProposalResponse.ProposedAd(
                                ad.getId(),
                                ad.getName(),
                                ad.getCreativePackageItemId(),
                                ad.getCreativeAssetId(),
                                ad.getCopyVariantId(),
                                ad.getPrimaryText(),
                                ad.getHeadline(),
                                ad.getDescription(),
                                ad.getCta(),
                                ad.getDestinationUrl()
                        ));
                    }
                } else {
                    JsonNode adsJson = adsetJson.get("ads");
                    if (adsJson != null && adsJson.isArray()) {
                    for (JsonNode adJson : adsJson) {
                        UUID creativeAssetId = null;
                        if (adJson.has("creative_asset_id")
                                && !adJson.get("creative_asset_id").isNull()
                                && !adJson.get("creative_asset_id").asText().equals("null")) {
                            try {
                                creativeAssetId = UUID.fromString(adJson.get("creative_asset_id").asText());
                            } catch (Exception ignored) { /* LLM might produce invalid UUID */ }
                        }

                            String primaryText = adJson.has("primary_text") ? adJson.get("primary_text").asText() : "";
                            String headline = adJson.has("headline") ? adJson.get("headline").asText() : "";
                            String description = adJson.has("description") ? adJson.get("description").asText() : "";
                            String cta = adJson.has("cta") ? adJson.get("cta").asText() : "LEARN_MORE";
                            String destinationUrl = adJson.has("url") ? adJson.get("url").asText() : "";
                            UUID copyVariantId = findMatchingCopyVariantId(agencyId, clientId, creativeAssetId,
                                primaryText, headline, description, cta);

                        Ad ad = new Ad();
                        ad.setAgencyId(agencyId);
                        ad.setClientId(clientId);
                        ad.setAdsetId(adset.getId());
                        ad.setName(adJson.has("name") ? adJson.get("name").asText() : "Ad");
                        ad.setCreativePackageItemId(creativeAssetId);
                            ad.setCreativeAssetId(creativeAssetId);
                            ad.setCopyVariantId(copyVariantId);
                            ad.setPrimaryText(primaryText);
                            ad.setHeadline(headline);
                            ad.setDescription(description);
                            ad.setCta(cta);
                            ad.setDestinationUrl(destinationUrl);
                        ad.setStatus("DRAFT");
                        ad.setCreatedAt(OffsetDateTime.now());
                        ad.setUpdatedAt(OffsetDateTime.now());
                        ad = adRepo.save(ad);

                        proposedAds.add(new CampaignProposalResponse.ProposedAd(
                                ad.getId(),
                                ad.getName(),
                            ad.getCreativePackageItemId(),
                                creativeAssetId,
                                copyVariantId,
                                primaryText,
                                headline,
                                description,
                                cta,
                                destinationUrl
                        ));
                    }
                    }
                }

                proposedAdsets.add(new CampaignProposalResponse.ProposedAdset(
                        adset.getId(),
                        adset.getName(),
                        adset.getDailyBudget(),
                        adset.getTargetingJson(),
                        optGoal,
                        proposedAds
                ));
            }
        }

        log.info("Created AI campaign proposal '{}' with {} adsets for client {}",
                campaignName, proposedAdsets.size(), clientId);

        return new CampaignProposalResponse(
                campaign.getId(), campaignName, objective, budgetType, campaignDailyBudget, "META", "DRAFT",
                rationale, suggestedBudget, estimatedResults, warnings, proposedAdsets);
    }

    private String normalizeBudgetType(String budgetType) {
        return "CBO".equalsIgnoreCase(budgetType) ? "CBO" : "ABO";
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

    private BigDecimal firstPositive(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return null;
    }

    private UUID findMatchingCopyVariantId(UUID agencyId, UUID clientId, UUID creativeAssetId,
                                           String primaryText, String headline, String description, String cta) {
        List<CopyVariant> candidates = new ArrayList<>();
        if (creativeAssetId != null) {
            candidates.addAll(copyRepo.findByCreativeAssetIdAndStatusOrderByCreatedAtDesc(creativeAssetId, "APPROVED"));
            if (candidates.isEmpty()) {
                candidates.addAll(copyRepo.findByCreativeAssetId(creativeAssetId));
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(copyRepo.findAllByAgencyIdAndClientId(agencyId, clientId));
        }

        String normalizedPrimary = normalize(primaryText);
        String normalizedHeadline = normalize(headline);
        String normalizedDescription = normalize(description);
        String normalizedCta = normalize(cta);

        return candidates.stream()
                .filter(variant -> normalizedPrimary.equals(normalize(variant.getPrimaryText())))
                .filter(variant -> normalizedHeadline.equals(normalize(variant.getHeadline())))
                .filter(variant -> normalizedDescription.equals(normalize(variant.getDescription())))
                .filter(variant -> normalizedCta.equals(normalize(variant.getCta())))
                .map(CopyVariant::getId)
                .findFirst()
                .orElse(null);
    }

    private String buildPackageAdName(CopyVariant copyVariant, CreativePackageItem item) {
        String headline = copyVariant != null ? copyVariant.getHeadline() : null;
        if (headline != null && !headline.isBlank()) {
            return headline;
        }
        return "Package Ad " + item.getId().toString().substring(0, 8);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    // ──────── Utilities ────────

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
