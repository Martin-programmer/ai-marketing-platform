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
            Evaluate the provided creative (image or video) for use in paid social / display campaigns. \
            If this is a video, describe: the storyline/narrative, key scenes, \
            text overlays, call to action, pacing/energy level, music mood (if detectable), \
            and overall production quality. \
            Return ONLY a JSON object (no markdown fences, no extra text) with exactly these keys:
            {
              "quality_score": <number 0-100>,
              "composition": "<brief assessment of layout, balance, focal point>",
              "color_palette": ["#hex1","#hex2","#hex3"],
              "text_readability": "<assessment of any text in the image/video>",
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
    private final AiContextBuilder aiContextBuilder;
    private final AiProperties aiProperties;

    public CreativeAnalyzerService(ClaudeApiClient claudeClient,
                                   CreativeAnalysisRepository analysisRepository,
                                   S3StorageService s3StorageService,
                                   AiContextBuilder aiContextBuilder,
                                   AiProperties aiProperties) {
        this.claudeClient = claudeClient;
        this.analysisRepository = analysisRepository;
        this.s3StorageService = s3StorageService;
        this.aiContextBuilder = aiContextBuilder;
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

        if (!isAnalyzableAsset(asset)) {
            log.info("Asset {} is not analyzable (type={}, mime={}), skipping",
                    asset.getId(), asset.getAssetType(), asset.getMimeType());
            return null;
        }

        // Check if analysis already exists
        if (analysisRepository.findByCreativeAssetId(asset.getId()).isPresent()) {
            log.info("Analysis already exists for asset {}", asset.getId());
            return analysisRepository.findByCreativeAssetId(asset.getId()).orElse(null);
        }

        log.info("Starting creative analysis for asset {} ({}, type={})",
                asset.getId(), asset.getOriginalFilename(), asset.getAssetType());

        String clientContext = aiContextBuilder.buildContext(asset.getAgencyId(), asset.getClientId());
        ClaudeApiClient.ClaudeResponse response = null;

        if ("IMAGE".equals(asset.getAssetType())) {
            // Existing flow: send image URL to Claude Vision
            String imageUrl = s3StorageService.generatePresignedGetUrl(asset.getS3Key());
            String userMessage = String.format(
                "Analyze this creative asset. File: %s, Type: %s.%n%nShared client context:%n%s%n%n"
                    + "Provide your analysis as the JSON object described in the system prompt.",
                asset.getOriginalFilename(), asset.getMimeType(), clientContext);

            response = claudeClient.sendVisionMessage(
                    SYSTEM_PROMPT, userMessage, imageUrl, asset.getMimeType(),
                    MODULE, asset.getAgencyId(), asset.getClientId());

        } else if ("VIDEO".equals(asset.getAssetType())) {
            // New flow: download video from S3, send to Claude as base64
            byte[] videoBytes = s3StorageService.downloadFile(asset.getS3Key());

            if (videoBytes != null && videoBytes.length <= 20 * 1024 * 1024) {
                // Video within Claude's size limit — send for full analysis
                String videoUserMessage = "Analyze this video creative. Watch the full video and describe what happens, " +
                    "the visual style, messaging, and how it could be used in advertising. " +
                    "File: " + asset.getOriginalFilename() + ", Type: " + asset.getMimeType() + "." +
                    "\n\nShared client context:\n" + clientContext + "\n\n" +
                    "Respond ONLY in valid JSON as described in the system prompt.";

                response = claudeClient.sendVideoMessage(SYSTEM_PROMPT,
                        videoUserMessage, videoBytes, asset.getMimeType(),
                        MODULE, asset.getAgencyId(), asset.getClientId());

            } else if (videoBytes != null) {
                // Video too large for Claude — fall back to metadata analysis
                log.warn("Video asset {} too large for Claude vision ({}MB), falling back to metadata analysis",
                        asset.getId(), videoBytes.length / (1024 * 1024));
                response = claudeClient.sendMessage(SYSTEM_PROMPT,
                    "This is a video creative that is too large to analyze visually. " +
                    "Based on metadata: filename=" + asset.getOriginalFilename() +
                    ", size=" + asset.getSizeBytes() + " bytes, mime=" + asset.getMimeType() +
                    ". Provide analysis based on what you can infer. " +
                    "\n\nShared client context:\n" + clientContext + "\n\n" +
                    "Respond ONLY in valid JSON as described in the system prompt.",
                    MODULE, asset.getAgencyId(), asset.getClientId());

            } else {
                // S3 download failed or S3 disabled
                log.warn("Could not download video asset {} from S3, skipping analysis", asset.getId());
                return null;
            }
        }

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

    private boolean isAnalyzableAsset(CreativeAsset asset) {
        if (asset == null || asset.getMimeType() == null) return false;
        String type = asset.getAssetType();
        if ("IMAGE".equals(type)) return asset.getMimeType().startsWith("image/");
        if ("VIDEO".equals(type)) return asset.getMimeType().startsWith("video/");
        return false;
    }
}
