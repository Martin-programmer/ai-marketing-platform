package com.amp.ai;

import com.amp.campaigns.Ad;
import com.amp.campaigns.AdRepository;
import com.amp.campaigns.Adset;
import com.amp.campaigns.AdsetRepository;
import com.amp.campaigns.CampaignRepository;
import com.amp.creatives.CopyVariant;
import com.amp.creatives.CopyVariantRepository;
import com.amp.creatives.CreativeAnalysis;
import com.amp.creatives.CreativeAnalysisRepository;
import com.amp.creatives.CreativeAsset;
import com.amp.creatives.CreativeAssetRepository;
import com.amp.creatives.CreativePackageItem;
import com.amp.creatives.CreativePackageItemRepository;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AiCrossModuleSupportService {

    private static final Logger log = LoggerFactory.getLogger(AiCrossModuleSupportService.class);
    private static final BigDecimal HIGH_QUALITY_THRESHOLD = BigDecimal.valueOf(75);

    private final CreativeAnalysisRepository creativeAnalysisRepository;
    private final CreativeAssetRepository creativeAssetRepository;
    private final CopyVariantRepository copyVariantRepository;
    private final CreativePackageItemRepository creativePackageItemRepository;
    private final AdRepository adRepository;
    private final AdsetRepository adsetRepository;
    private final CampaignRepository campaignRepository;
    private final InsightDailyRepository insightDailyRepository;
    private final AiAudienceSuggestionRepository aiAudienceSuggestionRepository;
    private final AiBudgetAnalysisRepository aiBudgetAnalysisRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final AiActionLogRepository aiActionLogRepository;
    private final ObjectMapper objectMapper;

    public AiCrossModuleSupportService(CreativeAnalysisRepository creativeAnalysisRepository,
                                       CreativeAssetRepository creativeAssetRepository,
                                       CopyVariantRepository copyVariantRepository,
                                       CreativePackageItemRepository creativePackageItemRepository,
                                       AdRepository adRepository,
                                       AdsetRepository adsetRepository,
                                       CampaignRepository campaignRepository,
                                       InsightDailyRepository insightDailyRepository,
                                       AiAudienceSuggestionRepository aiAudienceSuggestionRepository,
                                       AiBudgetAnalysisRepository aiBudgetAnalysisRepository,
                                       AiSuggestionRepository aiSuggestionRepository,
                                       AiActionLogRepository aiActionLogRepository,
                                       ObjectMapper objectMapper) {
        this.creativeAnalysisRepository = creativeAnalysisRepository;
        this.creativeAssetRepository = creativeAssetRepository;
        this.copyVariantRepository = copyVariantRepository;
        this.creativePackageItemRepository = creativePackageItemRepository;
        this.adRepository = adRepository;
        this.adsetRepository = adsetRepository;
        this.campaignRepository = campaignRepository;
        this.insightDailyRepository = insightDailyRepository;
        this.aiAudienceSuggestionRepository = aiAudienceSuggestionRepository;
        this.aiBudgetAnalysisRepository = aiBudgetAnalysisRepository;
        this.aiSuggestionRepository = aiSuggestionRepository;
        this.aiActionLogRepository = aiActionLogRepository;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<CreativeSuggestionEnrichment> findCreativeSuggestionEnrichmentAsync(
            UUID agencyId, UUID clientId, String scopeType, UUID scopeId, boolean includeCopyRefreshHints) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return buildCreativeSuggestionEnrichment(agencyId, clientId, scopeType, scopeId, includeCopyRefreshHints);
            } catch (Exception e) {
                log.warn("Creative suggestion enrichment failed for client {} scope {} {}: {}",
                        clientId, scopeType, scopeId, e.getMessage());
                return CreativeSuggestionEnrichment.empty();
            }
        });
    }

    public CompletableFuture<String> buildTopPerformingAdTextSummaryAsync(UUID agencyId, UUID clientId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return buildTopPerformingAdTextSummary(agencyId, clientId);
            } catch (Exception e) {
                log.warn("Top-performing ad text lookup failed for client {}: {}", clientId, e.getMessage());
                return "No reliable top-performing ad text data was available for this client.";
            }
        });
    }

    public CompletableFuture<String> buildCampaignCreatorSectionsAsync(UUID agencyId, UUID clientId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return buildCampaignCreatorSections(agencyId, clientId);
            } catch (Exception e) {
                log.warn("Campaign creator cross-module context failed for client {}: {}", clientId, e.getMessage());
                return "";
            }
        });
    }

    public CompletableFuture<String> buildAiActivitySummaryAsync(UUID agencyId, UUID clientId,
                                                                 LocalDate periodStart, LocalDate periodEnd,
                                                                 boolean includeActionDetails,
                                                                 String label) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return buildAiActivitySummary(agencyId, clientId, periodStart, periodEnd, includeActionDetails, label);
            } catch (Exception e) {
                log.warn("AI activity summary failed for client {}: {}", clientId, e.getMessage());
                return "";
            }
        });
    }

    public Optional<CreativeAnalysis> findAnalysisForScope(UUID agencyId, UUID clientId, String scopeType, UUID scopeId) {
        UUID assetId = resolvePrimaryCreativeAssetId(agencyId, clientId, scopeType, scopeId);
        if (assetId == null) {
            return Optional.empty();
        }
        return creativeAnalysisRepository.findByCreativeAssetId(assetId);
    }

    private CreativeSuggestionEnrichment buildCreativeSuggestionEnrichment(UUID agencyId, UUID clientId,
                                                                           String scopeType, UUID scopeId,
                                                                           boolean includeCopyRefreshHints) {
        UUID campaignId = resolveCampaignId(agencyId, scopeType, scopeId);
        Set<UUID> usedAssetIds = campaignId != null
                ? collectUsedCreativeAssetIds(agencyId, clientId, campaignId)
                : Set.of();

        Map<UUID, CreativeAsset> assetById = creativeAssetRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream()
                .collect(Collectors.toMap(CreativeAsset::getId, asset -> asset, (left, right) -> left));

        List<CreativeAnalysis> allAnalyses = creativeAnalysisRepository
                .findAllByAgencyIdAndClientIdOrderByQualityScoreDescCreatedAtDesc(agencyId, clientId)
                .stream()
                .filter(a -> assetById.containsKey(a.getCreativeAssetId()))
                .filter(a -> !usedAssetIds.contains(a.getCreativeAssetId()))
                .toList();

        List<CreativeAnalysis> shortlisted = allAnalyses.stream()
                .filter(a -> a.getQualityScore() != null && a.getQualityScore().compareTo(HIGH_QUALITY_THRESHOLD) >= 0)
                .limit(3)
                .toList();
        if (shortlisted.isEmpty()) {
            shortlisted = allAnalyses.stream().limit(3).toList();
        }

        List<Map<String, Object>> recommendedCreatives = new ArrayList<>();
        List<String> filenames = new ArrayList<>();
        for (CreativeAnalysis analysis : shortlisted) {
            CreativeAsset asset = assetById.get(analysis.getCreativeAssetId());
            if (asset == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("asset_id", analysis.getCreativeAssetId());
            item.put("filename", asset.getOriginalFilename());
            item.put("quality_score", analysis.getQualityScore());
            recommendedCreatives.add(item);
            filenames.add(asset.getOriginalFilename());
        }

        StringBuilder rationaleSuffix = new StringBuilder();
        if (!filenames.isEmpty()) {
            rationaleSuffix.append(" We recommend testing these creatives from your library: ")
                    .append(String.join(", ", filenames))
                    .append(".");
        }

        if (includeCopyRefreshHints) {
            UUID affectedAssetId = resolvePrimaryCreativeAssetId(agencyId, clientId, scopeType, scopeId);
            if (affectedAssetId == null && !recommendedCreatives.isEmpty()) {
                Object id = recommendedCreatives.get(0).get("asset_id");
                if (id instanceof UUID uuid) {
                    affectedAssetId = uuid;
                }
            }

            List<CopyVariant> draftVariants = affectedAssetId != null
                    ? copyVariantRepository.findByCreativeAssetIdAndStatusOrderByCreatedAtDesc(affectedAssetId, "DRAFT")
                    : List.of();

            if (!draftVariants.isEmpty()) {
                String headlines = draftVariants.stream()
                        .map(CopyVariant::getHeadline)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .limit(3)
                        .collect(Collectors.joining(", "));
                if (!headlines.isBlank()) {
                    rationaleSuffix.append(" Existing DRAFT copy variants for the affected creative include: ")
                            .append(headlines)
                            .append(".");
                }
            } else {
                rationaleSuffix.append(" Consider generating new copy variants.");
            }
        }

        return new CreativeSuggestionEnrichment(rationaleSuffix.toString(), recommendedCreatives);
    }

    private String buildTopPerformingAdTextSummary(UUID agencyId, UUID clientId) {
        LocalDate today = LocalDate.now();
        List<InsightDaily> insights = insightDailyRepository.findAllByAgencyIdAndClientIdAndDateBetween(
                agencyId, clientId, today.minusDays(30), today)
                .stream()
                .filter(i -> "AD".equalsIgnoreCase(i.getEntityType()))
                .toList();

        if (insights.isEmpty()) {
            return "No ad-level performance history was available for this client.";
        }

        Map<UUID, List<InsightDaily>> byAd = insights.stream()
                .collect(Collectors.groupingBy(InsightDaily::getEntityId));
        Map<UUID, Ad> adsById = adRepository.findAllByAgencyIdAndClientId(agencyId, clientId).stream()
                .collect(Collectors.toMap(Ad::getId, ad -> ad, (left, right) -> left));

        List<AdPerformanceSample> topAds = byAd.entrySet().stream()
                .map(entry -> {
                    UUID adId = entry.getKey();
                    List<InsightDaily> data = entry.getValue();
                    double avgCtr = data.stream()
                            .map(InsightDaily::getCtr)
                            .filter(Objects::nonNull)
                            .mapToDouble(BigDecimal::doubleValue)
                            .average().orElse(0);
                    double avgCpc = data.stream()
                            .map(InsightDaily::getCpc)
                            .filter(Objects::nonNull)
                            .mapToDouble(BigDecimal::doubleValue)
                            .average().orElse(Double.MAX_VALUE);
                    long impressions = data.stream().mapToLong(InsightDaily::getImpressions).sum();
                    return new AdPerformanceSample(adId, avgCtr, avgCpc, impressions, adsById.get(adId));
                })
                .filter(sample -> sample.ad() != null)
                .sorted(Comparator.comparingDouble(AdPerformanceSample::avgCtr).reversed()
                        .thenComparingDouble(AdPerformanceSample::avgCpc)
                        .thenComparingLong(AdPerformanceSample::impressions).reversed())
                .limit(3)
                .toList();

        if (topAds.isEmpty()) {
            return "No reusable ad text examples were found for this client.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("These ad texts have performed best for this client:\n");
        for (AdPerformanceSample sample : topAds) {
            String text = resolveAdText(sample.ad());
            sb.append("- '")
                    .append(truncate(singleLine(text), 160))
                    .append("' — CTR: ")
                    .append(formatDecimal(sample.avgCtr(), 2))
                    .append("%, CPC: $")
                    .append(formatDecimal(sample.avgCpc(), 2))
                    .append("\n");
        }
        sb.append("Generate new variants that are similar in style but fresh.");
        return sb.toString();
    }

    private String buildCampaignCreatorSections(UUID agencyId, UUID clientId) {
        StringBuilder sb = new StringBuilder();

        AiAudienceSuggestion latestAudience = aiAudienceSuggestionRepository
                .findTop1ByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId, clientId);
        if (latestAudience != null && latestAudience.getSuggestionJson() != null) {
            sb.append("AI previously recommended these audiences: ")
                    .append(summarizeAudienceJson(latestAudience.getSuggestionJson()))
                    .append("\n\n");
        }

        AiBudgetAnalysis latestBudget = aiBudgetAnalysisRepository
                .findTop1ByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId, clientId);
        if (latestBudget != null && latestBudget.getAnalysisJson() != null) {
            sb.append("AI budget analysis suggests: ")
                    .append(summarizeBudgetJson(latestBudget.getAnalysisJson()))
                    .append("\n\n");
        }

        List<AiSuggestion> diagnostics = aiSuggestionRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream()
                .filter(s -> "DIAGNOSTIC".equalsIgnoreCase(s.getSuggestionType()))
                .sorted(Comparator.comparing(AiSuggestion::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
        if (!diagnostics.isEmpty()) {
            sb.append("Current issues detected by AI:\n");
            diagnostics.forEach(s -> sb.append("- ")
                    .append(truncate(singleLine(s.getRationale()), 180))
                    .append("\n"));
            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildAiActivitySummary(UUID agencyId, UUID clientId,
                                          LocalDate periodStart, LocalDate periodEnd,
                                          boolean includeActionDetails,
                                          String label) {
        OffsetDateTime start = periodStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endExclusive = periodEnd.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<AiSuggestion> suggestions = aiSuggestionRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream()
                .filter(s -> inRange(s.getCreatedAt(), start, endExclusive))
                .toList();
        List<AiActionLog> actions = aiActionLogRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream()
                .filter(a -> inRange(a.getCreatedAt(), start, endExclusive))
                .toList();
        List<InsightDaily> insights = insightDailyRepository.findAllByAgencyIdAndClientIdAndDateBetween(
                agencyId, clientId, periodStart, periodEnd);

        long analyzedCampaigns = insights.stream()
                .filter(i -> "CAMPAIGN".equalsIgnoreCase(i.getEntityType()))
                .map(InsightDaily::getEntityId)
                .distinct()
                .count();
        long generated = suggestions.size();
        long approved = suggestions.stream().filter(s -> "APPROVED".equalsIgnoreCase(s.getStatus())
                || "APPLIED".equalsIgnoreCase(s.getStatus())
                || "APPLYING".equalsIgnoreCase(s.getStatus())).count();
        long rejected = suggestions.stream().filter(s -> "REJECTED".equalsIgnoreCase(s.getStatus())).count();
        long applied = suggestions.stream().filter(s -> "APPLIED".equalsIgnoreCase(s.getStatus())).count();
        long anomalies = suggestions.stream().filter(s -> "DIAGNOSTIC".equalsIgnoreCase(s.getSuggestionType())).count();

        Map<UUID, AiSuggestion> suggestionById = suggestions.stream()
                .collect(Collectors.toMap(AiSuggestion::getId, s -> s, (left, right) -> left));
        String keyOptimization = actions.stream()
                .filter(AiActionLog::isSuccess)
                .sorted(Comparator.comparing(AiActionLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(action -> summarizeAction(action, suggestionById.get(action.getSuggestionId())))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("No major AI action was applied during this period.");

        String periodLabel = label == null || label.isBlank() ? "period" : label;
        StringBuilder sb = new StringBuilder();
        sb.append("This ").append(periodLabel).append(" our AI: analyzed ")
                .append(analyzedCampaigns)
                .append(" campaigns, generated ")
                .append(generated)
                .append(" suggestions, ")
                .append(applied)
                .append(" were approved and applied. Key optimization: ")
                .append(keyOptimization)
                .append("\n");
        sb.append("AI activity details:\n")
                .append("- Suggestions generated: ").append(generated).append("\n")
                .append("- Suggestions approved: ").append(approved).append("\n")
                .append("- Suggestions rejected: ").append(rejected).append("\n")
                .append("- Suggestions applied: ").append(applied).append("\n")
                .append("- Anomalies detected: ").append(anomalies).append("\n");

        if (!actions.isEmpty()) {
            long successfulActions = actions.stream().filter(AiActionLog::isSuccess).count();
            sb.append("- Successful action executions: ").append(successfulActions).append("\n");
        }

        if (includeActionDetails && !actions.isEmpty()) {
            sb.append("Actions taken this month:\n");
            actions.stream()
                    .sorted(Comparator.comparing(AiActionLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(5)
                    .forEach(action -> sb.append("- ")
                            .append(summarizeAction(action, suggestionById.get(action.getSuggestionId())))
                            .append(" | Result: ")
                            .append(summarizeSnapshotDiff(action.getResultSnapshotJson()))
                            .append("\n"));
        }

        return sb.toString();
    }

    private String summarizeAudienceJson(String json) {
        JsonNode node = parseJson(json);
        if (node == null) {
            return truncate(singleLine(json), 220);
        }

        List<String> parts = new ArrayList<>();
        collectTextValues(node, parts, Set.of("name", "description", "interest", "audience", "segment"), 6);
        return parts.isEmpty() ? truncate(singleLine(json), 220) : String.join("; ", parts);
    }

    private String summarizeBudgetJson(String json) {
        JsonNode node = parseJson(json);
        if (node == null) {
            return truncate(singleLine(json), 220);
        }

        List<String> bits = new ArrayList<>();
        appendJsonField(bits, node, "pacing_summary");
        appendJsonField(bits, node, "narrative");
        appendJsonField(bits, node, "optimal_budget");
        appendJsonField(bits, node, "recommended_daily_budget");
        appendJsonField(bits, node, "recommended_monthly_budget");
        appendJsonField(bits, node, "day_of_week_pattern");
        return bits.isEmpty() ? truncate(singleLine(json), 220) : String.join(" | ", bits);
    }

    private void appendJsonField(List<String> bits, JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            bits.add(humanize(field) + ": " + truncate(singleLine(node.get(field).asText(node.get(field).toString())), 120));
        }
    }

    private void collectTextValues(JsonNode node, List<String> out, Set<String> preferredKeys, int maxItems) {
        if (node == null || out.size() >= maxItems) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (out.size() >= maxItems) {
                    return;
                }
                if (preferredKeys.contains(entry.getKey()) && entry.getValue().isValueNode()) {
                    String value = singleLine(entry.getValue().asText());
                    if (!value.isBlank()) {
                        out.add(value);
                    }
                } else {
                    collectTextValues(entry.getValue(), out, preferredKeys, maxItems);
                }
            });
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                if (out.size() >= maxItems) {
                    break;
                }
                collectTextValues(item, out, preferredKeys, maxItems);
            }
        }
    }

    private String summarizeAction(AiActionLog action, AiSuggestion suggestion) {
        if (action == null) {
            return null;
        }
        String type = suggestion != null ? suggestion.getSuggestionType() : "AI_ACTION";
        String rationale = suggestion != null ? truncate(singleLine(suggestion.getRationale()), 140) : "Applied AI optimization";
        return type + " — " + rationale;
    }

    private String summarizeSnapshotDiff(String snapshotJson) {
        JsonNode node = parseJson(snapshotJson);
        if (node == null || !node.isObject()) {
            return "No before/after snapshot available";
        }

        JsonNode before = node.get("before");
        JsonNode after = node.get("after");
        if (before == null || after == null) {
            return truncate(singleLine(snapshotJson), 160);
        }

        List<String> changes = new ArrayList<>();
        compareField(changes, before, after, "status");
        compareField(changes, before, after, "daily_budget");
        compareField(changes, before, after, "lifetime_budget");
        compareField(changes, before, after, "name");
        return changes.isEmpty() ? "Before/after data captured with no major field change summary" : String.join(", ", changes);
    }

    private void compareField(List<String> changes, JsonNode before, JsonNode after, String field) {
        if (before != null && after != null && before.has(field) && after.has(field)) {
            String beforeVal = singleLine(before.get(field).asText());
            String afterVal = singleLine(after.get(field).asText());
            if (!Objects.equals(beforeVal, afterVal)) {
                changes.add(field + " " + beforeVal + " → " + afterVal);
            }
        }
    }

    private UUID resolveCampaignId(UUID agencyId, String scopeType, UUID scopeId) {
        if (scopeId == null || scopeType == null) {
            return null;
        }
        return switch (scopeType.toUpperCase()) {
            case "CAMPAIGN" -> scopeId;
            case "ADSET" -> adsetRepository.findByIdAndAgencyId(scopeId, agencyId).map(Adset::getCampaignId).orElse(null);
            case "AD" -> adRepository.findByIdAndAgencyId(scopeId, agencyId)
                    .flatMap(ad -> adsetRepository.findByIdAndAgencyId(ad.getAdsetId(), agencyId))
                    .map(Adset::getCampaignId)
                    .orElse(null);
            default -> null;
        };
    }

    private UUID resolvePrimaryCreativeAssetId(UUID agencyId, UUID clientId, String scopeType, UUID scopeId) {
        if (scopeId == null || scopeType == null) {
            return null;
        }

        if ("AD".equalsIgnoreCase(scopeType)) {
            return adRepository.findByIdAndAgencyId(scopeId, agencyId)
                    .map(ad -> resolveCreativeAssetId(ad.getCreativePackageItemId()))
                    .orElse(null);
        }

        UUID campaignId = resolveCampaignId(agencyId, scopeType, scopeId);
        if (campaignId == null) {
            return null;
        }

        return collectAdsForCampaign(campaignId).stream()
                .map(Ad::getCreativePackageItemId)
                .map(this::resolveCreativeAssetId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Set<UUID> collectUsedCreativeAssetIds(UUID agencyId, UUID clientId, UUID campaignId) {
        Set<UUID> assetIds = new HashSet<>();
        for (Ad ad : collectAdsForCampaign(campaignId)) {
            UUID assetId = resolveCreativeAssetId(ad.getCreativePackageItemId());
            if (assetId != null) {
                assetIds.add(assetId);
            }
        }
        return assetIds;
    }

    private List<Ad> collectAdsForCampaign(UUID campaignId) {
        List<Ad> ads = new ArrayList<>();
        for (Adset adset : adsetRepository.findAllByCampaignId(campaignId)) {
            ads.addAll(adRepository.findAllByAdsetId(adset.getId()));
        }
        return ads;
    }

    private UUID resolveCreativeAssetId(UUID creativePackageItemOrAssetId) {
        if (creativePackageItemOrAssetId == null) {
            return null;
        }
        Optional<CreativePackageItem> packageItem = creativePackageItemRepository.findById(creativePackageItemOrAssetId);
        if (packageItem.isPresent()) {
            return packageItem.get().getCreativeAssetId();
        }
        return creativeAssetRepository.findById(creativePackageItemOrAssetId)
                .map(CreativeAsset::getId)
                .orElse(null);
    }

    private String resolveAdText(Ad ad) {
        if (ad == null) {
            return "Untitled ad";
        }

        UUID creativeRef = ad.getCreativePackageItemId();
        if (creativeRef != null) {
            Optional<CreativePackageItem> packageItem = creativePackageItemRepository.findById(creativeRef);
            if (packageItem.isPresent()) {
                CreativePackageItem item = packageItem.get();
                Optional<CopyVariant> copyVariant = copyVariantRepository.findById(item.getCopyVariantId());
                if (copyVariant.isPresent()) {
                    return renderCopyText(copyVariant.get());
                }
                List<CopyVariant> assetVariants = copyVariantRepository.findByCreativeAssetId(item.getCreativeAssetId());
                if (!assetVariants.isEmpty()) {
                    return renderCopyText(assetVariants.get(0));
                }
            }

            List<CopyVariant> directVariants = copyVariantRepository.findByCreativeAssetId(creativeRef);
            if (!directVariants.isEmpty()) {
                return renderCopyText(directVariants.get(0));
            }
        }

        return ad.getName() != null ? ad.getName() : "Untitled ad";
    }

    private String renderCopyText(CopyVariant copyVariant) {
        List<String> parts = new ArrayList<>();
        if (copyVariant.getPrimaryText() != null && !copyVariant.getPrimaryText().isBlank()) {
            parts.add(copyVariant.getPrimaryText().trim());
        }
        if (copyVariant.getHeadline() != null && !copyVariant.getHeadline().isBlank()) {
            parts.add(copyVariant.getHeadline().trim());
        }
        if (copyVariant.getDescription() != null && !copyVariant.getDescription().isBlank()) {
            parts.add(copyVariant.getDescription().trim());
        }
        return parts.isEmpty() ? "Untitled copy variant" : String.join(" | ", parts);
    }

    private boolean inRange(OffsetDateTime createdAt, OffsetDateTime start, OffsetDateTime endExclusive) {
        return createdAt != null && !createdAt.isBefore(start) && createdAt.isBefore(endExclusive);
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String humanize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace('_', ' ');
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = text.trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }

    private String singleLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String formatDecimal(double value, int scale) {
        if (value == Double.MAX_VALUE || Double.isNaN(value) || Double.isInfinite(value)) {
            return "0.00";
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    public record CreativeSuggestionEnrichment(String rationaleSuffix,
                                               List<Map<String, Object>> recommendedCreatives) {
        public static CreativeSuggestionEnrichment empty() {
            return new CreativeSuggestionEnrichment("", List.of());
        }
    }

    private record AdPerformanceSample(UUID adId, double avgCtr, double avgCpc, long impressions, Ad ad) {}
}