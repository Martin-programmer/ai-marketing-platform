package com.amp.ai;

import com.amp.clients.Client;
import com.amp.clients.ClientProfile;
import com.amp.clients.ClientProfileRepository;
import com.amp.clients.ClientRepository;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.creatives.CopyVariant;
import com.amp.creatives.CopyVariantRepository;
import com.amp.creatives.CreativeAnalysis;
import com.amp.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates adaptive ad-copy variants using Claude, informed by the
 * creative analysis result and the client's industry / profile.
 * <p>
 * Produces 5 variants per request, each tailored to a different platform tone
 * (e.g. Facebook, Instagram, Google Display, LinkedIn, general).
 */
@Service
public class CopyFactoryService {

    private static final Logger log = LoggerFactory.getLogger(CopyFactoryService.class);
    private static final String MODULE = "copy_factory";

    private static final String SYSTEM_PROMPT = """
            You are an expert digital-advertising copywriter. \
            Generate ad-copy variants based on the provided context. \
            Return ONLY a JSON array (no markdown fences, no extra text) of exactly 5 objects, \
            each with these keys:
            [
              {
                "platform": "<target platform>",
                "language": "en",
                "primary_text": "<main ad body text, max 125 chars>",
                "headline": "<headline, max 40 chars>",
                "description": "<description line, max 30 chars>",
                "cta": "<call to action, e.g. LEARN_MORE, SHOP_NOW, SIGN_UP, GET_OFFER, CONTACT_US>"
              }
            ]
            Generate variants for these platforms in order: \
            Facebook Feed, Instagram Story, Google Display, LinkedIn Sponsored, General/Multi-platform.
            """;

    private final ClaudeApiClient claudeClient;
    private final CopyVariantRepository copyVariantRepository;
    private final ClientRepository clientRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final AiProperties aiProperties;

    public CopyFactoryService(ClaudeApiClient claudeClient,
                               CopyVariantRepository copyVariantRepository,
                               ClientRepository clientRepository,
                               ClientProfileRepository clientProfileRepository,
                               AiProperties aiProperties) {
        this.claudeClient = claudeClient;
        this.copyVariantRepository = copyVariantRepository;
        this.clientRepository = clientRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.aiProperties = aiProperties;
    }

    /**
     * Generate 5 copy variants from a creative analysis result.
     *
     * @param analysis the analysis result (with JSON data)
     * @param userId   the user ID to attribute as creator
     * @return list of persisted copy variants
     */
    @Transactional
    public List<CopyVariant> generateCopy(CreativeAnalysis analysis, UUID userId) {
        if (!aiProperties.getAnalyzer().isEnabled()) {
            log.info("AI analyzer disabled — skipping copy generation for asset {}", analysis.getCreativeAssetId());
            return List.of();
        }

        UUID agencyId = analysis.getAgencyId();
        UUID clientId = analysis.getClientId();

        // Build context from client info
        String clientContext = buildClientContext(agencyId, clientId);

        String userMessage = String.format(
                "Generate 5 ad-copy variants for this creative.\n\n"
                        + "--- Client Context ---\n%s\n\n"
                        + "--- Creative Analysis ---\n%s\n\n"
                        + "Create compelling, platform-optimized copy that aligns with the brand and leverages the creative's strengths.",
                clientContext, analysis.getAnalysisJson());

        ClaudeApiClient.ClaudeResponse response = claudeClient.sendMessage(
                SYSTEM_PROMPT, userMessage, MODULE, agencyId, clientId);

        if (!response.isSuccess()) {
            log.error("Copy generation failed for asset {}: {}", analysis.getCreativeAssetId(), response.error());
            return List.of();
        }

        // Parse the JSON array
        JsonNode json = claudeClient.parseJson(response.text());
        if (json == null || !json.isArray()) {
            log.error("Copy generation returned invalid JSON for asset {}", analysis.getCreativeAssetId());
            return List.of();
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<CopyVariant> variants = new ArrayList<>();

        for (JsonNode item : json) {
            CopyVariant cv = new CopyVariant();
            cv.setAgencyId(agencyId);
            cv.setClientId(clientId);
            cv.setCreativeAssetId(analysis.getCreativeAssetId());
            cv.setLanguage(textOrDefault(item, "language", "en"));
            cv.setPrimaryText(textOrDefault(item, "primary_text", ""));
            cv.setHeadline(textOrDefault(item, "headline", ""));
            cv.setDescription(textOrDefault(item, "description", ""));
            cv.setCta(textOrDefault(item, "cta", "LEARN_MORE"));
            cv.setStatus("DRAFT");
            cv.setCreatedBy(userId);
            cv.setCreatedAt(now);
            cv.setUpdatedAt(now);

            variants.add(copyVariantRepository.save(cv));
        }

        log.info("Generated {} copy variants for asset {} (client {})",
                variants.size(), analysis.getCreativeAssetId(), clientId);
        return variants;
    }

    /**
     * Generate copy for a specific asset by ID (manual trigger).
     */
    @Transactional
    public List<CopyVariant> generateCopyForAsset(UUID agencyId, UUID assetId,
                                                   com.amp.creatives.CreativeAnalysisRepository analysisRepo) {
        CreativeAnalysis analysis = analysisRepo.findByCreativeAssetId(assetId)
                .orElseThrow(() -> new IllegalStateException(
                        "No analysis found for asset " + assetId + ". Run analysis first."));

        UUID userId = TenantContextHolder.require().getUserId();
        return generateCopy(analysis, userId);
    }

    private String buildClientContext(UUID agencyId, UUID clientId) {
        StringBuilder sb = new StringBuilder();

        clientRepository.findByIdAndAgencyId(clientId, agencyId).ifPresent(client -> {
            sb.append("Business name: ").append(client.getName()).append("\n");
            if (client.getIndustry() != null) {
                sb.append("Industry: ").append(client.getIndustry()).append("\n");
            }
        });

        clientProfileRepository.findByClientId(clientId).ifPresent(profile -> {
            if (profile.getProfileJson() != null && !profile.getProfileJson().isBlank()) {
                sb.append("Business profile: ").append(profile.getProfileJson()).append("\n");
            }
        });

        if (sb.isEmpty()) {
            sb.append("No additional client context available.");
        }

        return sb.toString();
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}
