package com.amp.ai;

import com.amp.creatives.CreativeAnalysis;
import com.amp.creatives.CreativeAnalysisRepository;
import com.amp.creatives.CreativeAsset;
import com.amp.creatives.S3StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Analyzes uploaded creative assets using Claude Vision API.
 * <p>
 * Sends the image to Claude with a structured prompt and stores the analysis
 * result (quality score, colour palette, composition notes, improvement suggestions)
 * in the {@code creative_analysis} table.
 */
@Service
public class CreativeAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(CreativeAnalyzerService.class);
    private static final String MODULE = "creative_analyzer";

    private static final String SYSTEM_PROMPT = """
            You are an expert digital-advertising creative analyst. \
            Evaluate the provided image for use in paid social / display campaigns. \
            Return ONLY a JSON object (no markdown fences, no extra text) with exactly these keys:
            {
              "quality_score": <number 0-100>,
              "composition": "<brief assessment of layout, balance, focal point>",
              "color_palette": ["#hex1","#hex2","#hex3"],
              "text_readability": "<assessment of any text in the image>",
              "brand_consistency": "<notes on logo placement, brand colours if detectable>",
              "platform_fit": {
                "facebook_feed": "<suitability 1-10 and why>",
                "instagram_story": "<suitability 1-10 and why>",
                "google_display": "<suitability 1-10 and why>"
              },
              "strengths": ["strength1","strength2"],
              "improvements": ["suggestion1","suggestion2"],
              "overall_summary": "<2-3 sentence executive summary>"
            }
            """;

    private final ClaudeApiClient claudeClient;
    private final CreativeAnalysisRepository analysisRepository;
    private final S3StorageService s3StorageService;
    private final AiProperties aiProperties;

    public CreativeAnalyzerService(ClaudeApiClient claudeClient,
                                   CreativeAnalysisRepository analysisRepository,
                                   S3StorageService s3StorageService,
                                   AiProperties aiProperties) {
        this.claudeClient = claudeClient;
        this.analysisRepository = analysisRepository;
        this.s3StorageService = s3StorageService;
        this.aiProperties = aiProperties;
    }

    /**
     * Analyze a creative asset image via Claude Vision.
     *
     * @param asset  the uploaded asset (must be an image with status READY)
     * @return the persisted {@link CreativeAnalysis}, or {@code null} if analysis is disabled or fails
     */
    @Transactional
    public CreativeAnalysis analyze(CreativeAsset asset) {
        if (!aiProperties.getAnalyzer().isEnabled()) {
            log.info("Creative analyzer disabled — skipping asset {}", asset.getId());
            return null;
        }

        if (!isAnalyzableImage(asset.getMimeType())) {
            log.info("Asset {} is not an image ({}), skipping analysis", asset.getId(), asset.getMimeType());
            return null;
        }

        // Check if analysis already exists
        if (analysisRepository.findByCreativeAssetId(asset.getId()).isPresent()) {
            log.info("Analysis already exists for asset {}", asset.getId());
            return analysisRepository.findByCreativeAssetId(asset.getId()).orElse(null);
        }

        log.info("Starting creative analysis for asset {} ({})", asset.getId(), asset.getOriginalFilename());

        // Generate presigned URL for Claude to access the image
        String imageUrl = s3StorageService.generatePresignedGetUrl(asset.getS3Key());

        String userMessage = String.format(
                "Analyze this creative asset. File: %s, Type: %s. "
                        + "Provide your analysis as the JSON object described in the system prompt.",
                asset.getOriginalFilename(), asset.getMimeType());

        ClaudeApiClient.ClaudeResponse response = claudeClient.sendVisionMessage(
                SYSTEM_PROMPT, userMessage, imageUrl, asset.getMimeType(),
                MODULE, asset.getAgencyId(), asset.getClientId());

        if (!response.isSuccess()) {
            log.error("Creative analysis failed for asset {}: {}", asset.getId(), response.error());
            return null;
        }

        // Parse JSON response
        JsonNode json = claudeClient.parseJson(response.text());
        BigDecimal qualityScore = BigDecimal.ZERO;
        String analysisJsonText = response.text();

        if (json != null) {
            analysisJsonText = json.toString();
            if (json.has("quality_score")) {
                qualityScore = BigDecimal.valueOf(json.get("quality_score").asDouble())
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                // Clamp to 0-100
                if (qualityScore.compareTo(BigDecimal.ZERO) < 0) qualityScore = BigDecimal.ZERO;
                if (qualityScore.compareTo(BigDecimal.valueOf(100)) > 0) qualityScore = BigDecimal.valueOf(100);
            }
        }

        // Persist analysis
        CreativeAnalysis analysis = new CreativeAnalysis();
        analysis.setAgencyId(asset.getAgencyId());
        analysis.setClientId(asset.getClientId());
        analysis.setCreativeAssetId(asset.getId());
        analysis.setAnalysisJson(analysisJsonText);
        analysis.setQualityScore(qualityScore);
        analysis.setCreatedAt(OffsetDateTime.now());

        CreativeAnalysis saved = analysisRepository.save(analysis);
        log.info("Creative analysis saved for asset {}, qualityScore={}", asset.getId(), qualityScore);
        return saved;
    }

    /**
     * Trigger analysis for a specific asset by ID (manual trigger).
     */
    @Transactional
    public CreativeAnalysis analyzeById(UUID agencyId, UUID assetId,
                                        com.amp.creatives.CreativeAssetRepository assetRepo) {
        CreativeAsset asset = assetRepo.findByIdAndAgencyId(assetId, agencyId)
                .orElseThrow(() -> new com.amp.common.exception.ResourceNotFoundException("CreativeAsset", assetId));
        return analyze(asset);
    }

    private boolean isAnalyzableImage(String mimeType) {
        if (mimeType == null) return false;
        return mimeType.startsWith("image/");
    }
}
