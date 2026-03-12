package com.amp.ai;

import com.amp.campaigns.Adset;
import com.amp.campaigns.AdsetRepository;
import com.amp.campaigns.Campaign;
import com.amp.campaigns.CampaignRepository;
import com.amp.clients.Client;
import com.amp.clients.ClientProfile;
import com.amp.clients.ClientProfileRepository;
import com.amp.clients.ClientRepository;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.creatives.CopyVariant;
import com.amp.creatives.CopyVariantRepository;
import com.amp.creatives.CreativeAnalysis;
import com.amp.creatives.CreativeAnalysisRepository;
import com.amp.creatives.CreativeAsset;
import com.amp.creatives.CreativeAssetRepository;
import com.amp.insights.InsightDaily;
import com.amp.insights.InsightDailyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(AiContextBuilder.class);
    private static final int MAX_CONTEXT_CHARS = 12000;

    private final ClientRepository clientRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final CreativeAssetRepository creativeAssetRepository;
    private final CreativeAnalysisRepository creativeAnalysisRepository;
    private final CopyVariantRepository copyVariantRepository;
    private final InsightDailyRepository insightDailyRepository;
    private final CampaignRepository campaignRepository;
    private final AdsetRepository adsetRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final AiActionLogRepository aiActionLogRepository;
    private final AiAudienceSuggestionRepository aiAudienceSuggestionRepository;
    private final AiBudgetAnalysisRepository aiBudgetAnalysisRepository;
    private final ObjectMapper objectMapper;

    public AiContextBuilder(ClientRepository clientRepository,
                            ClientProfileRepository clientProfileRepository,
                            CreativeAssetRepository creativeAssetRepository,
                            CreativeAnalysisRepository creativeAnalysisRepository,
                            CopyVariantRepository copyVariantRepository,
                            InsightDailyRepository insightDailyRepository,
                            CampaignRepository campaignRepository,
                            AdsetRepository adsetRepository,
                            AiSuggestionRepository aiSuggestionRepository,
                            AiActionLogRepository aiActionLogRepository,
                            AiAudienceSuggestionRepository aiAudienceSuggestionRepository,
                            AiBudgetAnalysisRepository aiBudgetAnalysisRepository,
                            ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.creativeAssetRepository = creativeAssetRepository;
        this.creativeAnalysisRepository = creativeAnalysisRepository;
        this.copyVariantRepository = copyVariantRepository;
        this.insightDailyRepository = insightDailyRepository;
        this.campaignRepository = campaignRepository;
        this.adsetRepository = adsetRepository;
        this.aiSuggestionRepository = aiSuggestionRepository;
        this.aiActionLogRepository = aiActionLogRepository;
        this.aiAudienceSuggestionRepository = aiAudienceSuggestionRepository;
        this.aiBudgetAnalysisRepository = aiBudgetAnalysisRepository;
        this.objectMapper = objectMapper;
    }

    @Cacheable(cacheNames = "aiContext", key = "#agencyId.toString() + ':' + #clientId.toString()", unless = "#result == null || #result.isBlank()")
    public String buildContext(UUID agencyId, UUID clientId) {
        Client client = clientRepository.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        ClientProfile profile = clientProfileRepository.findByClientId(clientId).orElse(null);
        JsonNode profileNode = parseJson(profile != null ? profile.getProfileJson() : null);
        JsonNode questionnaireNode = resolveQuestionnaireNode(profileNode);

        List<CreativeAsset> assets = creativeAssetRepository.findAllByAgencyIdAndClientId(agencyId, clientId);
        Map<UUID, CreativeAsset> assetById = assets.stream()
                .collect(Collectors.toMap(CreativeAsset::getId, asset -> asset, (left, right) -> left));
        List<CreativeAnalysis> analyses = creativeAnalysisRepository
                .findTop5ByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId, clientId);
        List<CopyVariant> copyVariants = copyVariantRepository
                .findTop5ByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId, clientId);
        List<InsightDaily> insights30d = insightDailyRepository.findAllByAgencyIdAndClientIdAndDateBetween(
                agencyId, clientId, LocalDate.now().minusDays(29), LocalDate.now());
        List<Campaign> campaigns = campaignRepository.findAllByAgencyIdAndClientId(agencyId, clientId);
        List<AiSuggestion> recentSuggestions = aiSuggestionRepository
                .findTop10ByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId, clientId);
        List<AiActionLog> recentActions = aiActionLogRepository
                .findTop5ByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId, clientId);
        List<AiAudienceSuggestion> audienceSuggestions = aiAudienceSuggestionRepository
                .findTop3ByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId, clientId);
        AiBudgetAnalysis savedBudgetAnalysis = aiBudgetAnalysisRepository
                .findTop1ByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId, clientId);

        StringBuilder sb = new StringBuilder();
        sb.append("Shared AI Client Context\n");
        sb.append("========================\n\n");

        appendClientBasics(sb, client, profile);
        appendQuestionnaire(sb, questionnaireNode, profile);
        appendProfileSummary(sb, profileNode);
        appendCreativeSummary(sb, assets, assetById, analyses);
        appendCopyVariants(sb, copyVariants);
        appendPerformanceSummary(sb, insights30d);
        appendCampaignSummary(sb, campaigns);
        appendRecentSuggestions(sb, recentSuggestions);
        appendRecentActions(sb, recentActions, recentSuggestions);
        appendAudienceSuggestions(sb, audienceSuggestions);
        appendBudgetAnalysis(sb, savedBudgetAnalysis);

        String context = trimToLimit(sb.toString());
        log.debug("Built AI context for client {} ({} chars)", clientId, context.length());
        return context;
    }

    private void appendClientBasics(StringBuilder sb, Client client, ClientProfile profile) {
        sb.append("Client Basics:\n");
        sb.append("- Name: ").append(defaultText(client.getName(), "Unknown")).append("\n");
        sb.append("- Industry: ").append(defaultText(client.getIndustry(), "Unknown")).append("\n");
        sb.append("- Currency: ").append(defaultText(client.getCurrency(), "USD")).append("\n");
        sb.append("- Timezone: ").append(defaultText(client.getTimezone(), "UTC")).append("\n");
        if (profile != null && hasText(profile.getWebsite())) {
            sb.append("- Website: ").append(profile.getWebsite()).append("\n");
        }
        sb.append("\n");
    }

    private void appendQuestionnaire(StringBuilder sb, JsonNode questionnaireNode, ClientProfile profile) {
        sb.append("Client Questionnaire:\n");
        sb.append("- Contact name: ").append(value(questionnaireNode, "contactName", "Unknown")).append("\n");
        sb.append("- Brand name: ").append(value(questionnaireNode, "brandName", "Unknown")).append("\n");
        sb.append("- Products: ").append(value(questionnaireNode, "productsDescription", "Not provided")).append("\n");
        sb.append("- Best sellers: ").append(value(questionnaireNode, "bestSellers", "Not provided")).append("\n");
        sb.append("- Average order value: ").append(value(questionnaireNode, "averageOrderValue", "Not provided")).append("\n");
        sb.append("- Profit margin: ").append(value(questionnaireNode, "profitMargin", "Not provided")).append("\n");
        sb.append("- Shipping info: ").append(value(questionnaireNode, "shippingInfo", "Not provided")).append("\n");
        sb.append("- Audiences: ").append(value(questionnaireNode, "audiences", "Not provided")).append("\n");
        sb.append("- Customer problem: ").append(value(questionnaireNode, "customerProblem", "Not provided")).append("\n");
        sb.append("- Objections: ").append(value(questionnaireNode, "customerObjections", "Not provided")).append("\n");
        sb.append("- USP: ").append(value(questionnaireNode, "usp", "Not provided")).append("\n");
        sb.append("- Competitors: ").append(value(questionnaireNode, "competitors", "Not provided")).append("\n");
        sb.append("- Tone: ").append(value(questionnaireNode, "tone", "Not provided")).append("\n");
        sb.append("- Target locations: ").append(value(questionnaireNode, "targetLocations", "Not provided")).append("\n");
        sb.append("- Marketing goal: ").append(value(questionnaireNode, "marketingGoal", "Not provided")).append("\n");
        sb.append("- Budget info: ").append(value(questionnaireNode, "adBudgetInfo", "Not provided")).append("\n");
        sb.append("- Previous ad experience: ").append(value(questionnaireNode, "previousAdExperience", "Not provided")).append("\n");
        sb.append("- Previous results: ").append(value(questionnaireNode, "previousResults", "Not provided")).append("\n");
        sb.append("- Challenges: ").append(value(questionnaireNode, "currentChallenges", "Not provided")).append("\n");
        sb.append("- Has creatives: ").append(value(questionnaireNode, "hasCreatives", "Unknown")).append("\n");
        sb.append("- Has tracking: ").append(value(questionnaireNode, "hasTracking", "Unknown")).append("\n");
        if (profile != null) {
            sb.append("- Questionnaire completed: ")
                    .append(Boolean.TRUE.equals(profile.getQuestionnaireCompleted()) ? "Yes" : "No")
                    .append("\n");
        }
        sb.append("\n");
    }

    private void appendProfileSummary(StringBuilder sb, JsonNode profileNode) {
        if (profileNode == null || profileNode.isMissingNode() || profileNode.isNull()) {
            return;
        }
        Set<String> reserved = new HashSet<>(Set.of(
                "questionnaire", "usp", "tone_of_voice", "target_audiences", "competitors"
        ));
        List<String> extraNotes = new ArrayList<>();
        profileNode.fields().forEachRemaining(entry -> {
            if (reserved.contains(entry.getKey())) {
                return;
            }
            if (entry.getValue().isValueNode()) {
                String text = compressWhitespace(entry.getValue().asText());
                if (hasText(text)) {
                    extraNotes.add(humanize(entry.getKey()) + ": " + truncate(text, 180));
                }
            }
        });
        if (!extraNotes.isEmpty()) {
            sb.append("Additional Profile Notes:\n");
            extraNotes.stream().limit(8).forEach(note -> sb.append("- ").append(note).append("\n"));
            sb.append("\n");
        }
    }

    private void appendCreativeSummary(StringBuilder sb,
                                       List<CreativeAsset> assets,
                                       Map<UUID, CreativeAsset> assetById,
                                       List<CreativeAnalysis> analyses) {
        sb.append("Creative Library Summary:\n");
        sb.append("- Total assets: ").append(assets.size()).append("\n");
        sb.append("- Analyzed creatives: ").append(analyses.size()).append("\n");
        if (analyses.isEmpty()) {
            sb.append("- No analyzed creatives saved yet.\n\n");
            return;
        }
        for (CreativeAnalysis analysis : analyses) {
            JsonNode analysisNode = parseJson(analysis.getAnalysisJson());
            CreativeAsset asset = assetById.get(analysis.getCreativeAssetId());
            sb.append("- Asset ").append(analysis.getCreativeAssetId());
            if (asset != null) {
                sb.append(" (").append(asset.getOriginalFilename()).append(", ")
                        .append(asset.getAssetType()).append(")");
            }
            sb.append(": quality=").append(numberText(analysis.getQualityScore()));
            appendIfPresentInline(sb, analysisNode, "visual_style", "visual style");
            appendIfPresentInline(sb, analysisNode, "funnel_fit", "funnel fit");
            appendArraySummary(sb, analysisNode, "suggested_hooks", "hooks", 3);
            appendArraySummary(sb, analysisNode, "strengths", "strengths", 2);
            appendIfPresentInline(sb, analysisNode, "overall_summary", "summary");
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void appendCopyVariants(StringBuilder sb, List<CopyVariant> copyVariants) {
        sb.append("Top Copy Variants:\n");
        if (copyVariants.isEmpty()) {
            sb.append("- No saved copy variants.\n\n");
            return;
        }
        for (CopyVariant variant : copyVariants) {
            sb.append("- Headline: ").append(truncate(variant.getHeadline(), 80))
                    .append(" | CTA: ").append(defaultText(variant.getCta(), "N/A"))
                    .append(" | Primary text: ").append(truncate(variant.getPrimaryText(), 140))
                    .append("\n");
        }
        sb.append("\n");
    }

    private void appendPerformanceSummary(StringBuilder sb, List<InsightDaily> insights) {
        sb.append("Historical Performance Summary (last 30 days):\n");
        if (insights.isEmpty()) {
            sb.append("- No performance data available.\n\n");
            return;
        }

        BigDecimal spend = BigDecimal.ZERO;
        long impressions = 0L;
        long clicks = 0L;
        BigDecimal conversions = BigDecimal.ZERO;
        BigDecimal conversionValue = BigDecimal.ZERO;
        BigDecimal frequencyTotal = BigDecimal.ZERO;
        int frequencyCount = 0;

        for (InsightDaily insight : insights) {
            spend = spend.add(zeroIfNull(insight.getSpend()));
            impressions += insight.getImpressions();
            clicks += insight.getClicks();
            conversions = conversions.add(zeroIfNull(insight.getConversions()));
            conversionValue = conversionValue.add(zeroIfNull(insight.getConversionValue()));
            if (insight.getFrequency() != null) {
                frequencyTotal = frequencyTotal.add(insight.getFrequency());
                frequencyCount++;
            }
        }

        BigDecimal ctr = impressions > 0
                ? BigDecimal.valueOf(clicks * 100.0 / impressions)
                : BigDecimal.ZERO;
        BigDecimal cpc = clicks > 0
                ? spend.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal roas = spend.compareTo(BigDecimal.ZERO) > 0
                ? conversionValue.divide(spend, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal frequency = frequencyCount > 0
                ? frequencyTotal.divide(BigDecimal.valueOf(frequencyCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        sb.append("- Spend: ").append(currency(spend)).append("\n");
        sb.append("- Impressions: ").append(impressions).append("\n");
        sb.append("- Clicks: ").append(clicks).append("\n");
        sb.append("- Conversions: ").append(numberText(conversions)).append("\n");
        sb.append("- ROAS: ").append(numberText(roas)).append("x\n");
        sb.append("- CTR: ").append(numberText(ctr)).append("%\n");
        sb.append("- CPC: ").append(currency(cpc)).append("\n");
        sb.append("- Frequency: ").append(numberText(frequency)).append("\n\n");
    }

    private void appendCampaignSummary(StringBuilder sb, List<Campaign> campaigns) {
        List<Campaign> activeCampaigns = campaigns.stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()) || "PUBLISHED".equalsIgnoreCase(c.getStatus()))
                .sorted(Comparator.comparing(Campaign::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();

        sb.append("Active Campaigns Summary:\n");
        if (activeCampaigns.isEmpty()) {
            sb.append("- No active campaigns.\n\n");
            return;
        }

        Map<UUID, BigDecimal> budgetByCampaign = new HashMap<>();
        for (Campaign campaign : activeCampaigns) {
            BigDecimal totalBudget = adsetRepository.findAllByCampaignId(campaign.getId()).stream()
                    .map(Adset::getDailyBudget)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            budgetByCampaign.put(campaign.getId(), totalBudget);
        }

        for (Campaign campaign : activeCampaigns) {
            sb.append("- ").append(truncate(campaign.getName(), 100))
                    .append(" | Objective: ").append(defaultText(campaign.getObjective(), "Unknown"))
                    .append(" | Status: ").append(defaultText(campaign.getStatus(), "Unknown"))
                    .append(" | Daily budget: ").append(currency(budgetByCampaign.getOrDefault(campaign.getId(), BigDecimal.ZERO)))
                    .append("\n");
        }
        sb.append("\n");
    }

    private void appendRecentSuggestions(StringBuilder sb, List<AiSuggestion> suggestions) {
        sb.append("Recent AI Suggestions:\n");
        if (suggestions.isEmpty()) {
            sb.append("- No recent suggestions.\n\n");
            return;
        }
        for (AiSuggestion suggestion : suggestions) {
            sb.append("- ").append(defaultText(suggestion.getSuggestionType(), "UNKNOWN"))
                    .append(" [").append(defaultText(suggestion.getStatus(), "UNKNOWN")).append("]")
                    .append(" — ").append(truncate(defaultText(suggestion.getRationale(), "No rationale"), 180))
                    .append("\n");
        }
        sb.append("\n");
    }

    private void appendRecentActions(StringBuilder sb,
                                     List<AiActionLog> actions,
                                     List<AiSuggestion> suggestions) {
        sb.append("Recent AI Actions:\n");
        if (actions.isEmpty()) {
            sb.append("- No recent applied actions.\n\n");
            return;
        }
        Map<UUID, String> suggestionTypes = suggestions.stream()
                .collect(Collectors.toMap(AiSuggestion::getId, AiSuggestion::getSuggestionType, (left, right) -> left));
        for (AiActionLog action : actions) {
            sb.append("- ").append(suggestionTypes.getOrDefault(action.getSuggestionId(), "UNKNOWN_ACTION"))
                    .append(" | Success: ").append(action.isSuccess() ? "yes" : "no")
                    .append(" | Result: ").append(summarizeActionResult(action))
                    .append("\n");
        }
        sb.append("\n");
    }

    private void appendAudienceSuggestions(StringBuilder sb, List<AiAudienceSuggestion> savedSuggestions) {
        sb.append("Saved Audience Suggestions:\n");
        if (savedSuggestions.isEmpty()) {
            sb.append("- None saved yet.\n\n");
            return;
        }
        int count = 0;
        for (AiAudienceSuggestion savedSuggestion : savedSuggestions) {
            JsonNode root = parseJson(savedSuggestion.getSuggestionJson());
            JsonNode audiences = root.path("recommended_audiences");
            if (audiences.isArray()) {
                for (JsonNode audience : audiences) {
                    if (count >= 5) {
                        break;
                    }
                    sb.append("- ").append(value(audience, "name", "Unnamed audience"))
                            .append(" | Stage: ").append(value(audience, "funnel_stage", "Unknown"))
                            .append(" | Confidence: ").append(value(audience, "confidence", "Unknown"))
                            .append(" | Why: ").append(truncate(value(audience, "rationale", "No rationale"), 140))
                            .append("\n");
                    count++;
                }
            }
            if (count >= 5) {
                break;
            }
        }
        JsonNode latest = parseJson(savedSuggestions.get(0).getSuggestionJson());
        if (latest.hasNonNull("strategy_notes")) {
            sb.append("- Strategy notes: ").append(truncate(latest.get("strategy_notes").asText(), 180)).append("\n");
        }
        sb.append("\n");
    }

    private void appendBudgetAnalysis(StringBuilder sb, AiBudgetAnalysis savedBudgetAnalysis) {
        sb.append("Saved Budget Analysis:\n");
        if (savedBudgetAnalysis == null) {
            sb.append("- None saved yet.\n\n");
            return;
        }
        JsonNode root = parseJson(savedBudgetAnalysis.getAnalysisJson());
        JsonNode pacing = root.path("pacing");
        JsonNode dayOfWeek = root.path("dayOfWeek");
        JsonNode ranking = root.path("campaignRanking");

        if (!pacing.isMissingNode()) {
            sb.append("- Pacing status: ").append(value(pacing, "pacingStatus", "Unknown"))
                    .append(" | Current month spend: ").append(currency(decimalValue(pacing.get("currentMonthSpend"))))
                    .append(" | Projected month spend: ").append(currency(decimalValue(pacing.get("projectedMonthSpend"))))
                    .append("\n");
        }
        if (!dayOfWeek.isMissingNode()) {
            sb.append("- Best day: ").append(value(dayOfWeek, "bestDay", "Unknown"))
                    .append(" | Worst day: ").append(value(dayOfWeek, "worstDay", "Unknown"))
                    .append(" | Recommendation: ").append(truncate(value(dayOfWeek, "recommendation", "None"), 140))
                    .append("\n");
        }
        if (ranking.isArray()) {
            int added = 0;
            for (JsonNode item : ranking) {
                if (added >= 3) {
                    break;
                }
                sb.append("- Campaign budget note: ").append(value(item, "campaignName", "Unknown"))
                        .append(" -> ").append(value(item, "suggestion", "MAINTAIN"))
                        .append(" (").append(truncate(value(item, "reason", "No reason"), 100)).append(")")
                        .append("\n");
                added++;
            }
        }
        if (root.hasNonNull("narrative")) {
            sb.append("- Narrative: ").append(truncate(root.get("narrative").asText(), 220)).append("\n");
        }
        sb.append("\n");
    }

    private JsonNode resolveQuestionnaireNode(JsonNode profileNode) {
        if (profileNode != null && profileNode.has("questionnaire") && profileNode.get("questionnaire").isObject()) {
            return profileNode.get("questionnaire");
        }
        return profileNode != null ? profileNode : objectMapper.createObjectNode();
    }

    private JsonNode parseJson(String json) {
        if (!hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.debug("Failed to parse context JSON: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private void appendIfPresentInline(StringBuilder sb, JsonNode root, String field, String label) {
        if (root != null && root.hasNonNull(field)) {
            String text = compressWhitespace(root.get(field).asText());
            if (hasText(text)) {
                sb.append(" | ").append(label).append(": ").append(truncate(text, 80));
            }
        }
    }

    private void appendArraySummary(StringBuilder sb, JsonNode root, String field, String label, int limit) {
        if (root == null || !root.has(field) || !root.get(field).isArray()) {
            return;
        }
        List<String> values = new ArrayList<>();
        root.get(field).forEach(node -> {
            String text = compressWhitespace(node.asText());
            if (hasText(text) && values.size() < limit) {
                values.add(truncate(text, 40));
            }
        });
        if (!values.isEmpty()) {
            sb.append(" | ").append(label).append(": ").append(String.join(", ", values));
        }
    }

    private String summarizeActionResult(AiActionLog action) {
        JsonNode snapshot = parseJson(action.getResultSnapshotJson());
        if (snapshot.hasNonNull("message")) {
            return truncate(snapshot.get("message").asText(), 100);
        }
        if (snapshot.hasNonNull("status")) {
            return truncate(snapshot.get("status").asText(), 80);
        }
        JsonNode response = parseJson(action.getMetaResponseJson());
        if (response.hasNonNull("message")) {
            return truncate(response.get("message").asText(), 100);
        }
        return action.isSuccess() ? "Applied successfully" : "Execution failed";
    }

    private String value(JsonNode node, String field, String fallback) {
        if (node != null && node.hasNonNull(field)) {
            String text = compressWhitespace(node.get(field).asText());
            if (hasText(text)) {
                return truncate(text, 220);
            }
        }
        return fallback;
    }

    private BigDecimal decimalValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String currency(BigDecimal value) {
        return "$" + numberText(value);
    }

    private String numberText(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        return safe.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String trimToLimit(String raw) {
        if (raw.length() <= MAX_CONTEXT_CHARS) {
            return raw;
        }
        StringBuilder trimmed = new StringBuilder();
        for (String line : raw.split("\\n")) {
            if (trimmed.length() + line.length() + 1 > MAX_CONTEXT_CHARS - 32) {
                break;
            }
            trimmed.append(line).append('\n');
        }
        trimmed.append("[Context truncated for token safety]");
        return trimmed.toString();
    }

    private String truncate(String text, int maxLength) {
        if (!hasText(text)) {
            return "";
        }
        String normalized = compressWhitespace(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String compressWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String humanize(String key) {
        String withSpaces = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return withSpaces.substring(0, 1).toUpperCase(Locale.ROOT) + withSpaces.substring(1);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }
}
