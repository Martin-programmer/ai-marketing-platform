package com.amp.ai;

import com.amp.ai.ClaudeApiClient.ClaudeResponse;
import com.amp.clients.Client;
import com.amp.clients.ClientProfile;
import com.amp.clients.ClientProfileRepository;
import com.amp.clients.ClientProfileRequest;
import com.amp.clients.ClientProfileService;
import com.amp.clients.ClientRepository;
import com.amp.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Analyses a client's website and auto-fills their profile using Claude.
 */
@Service
public class ClientBrieferService {

    private static final Logger log = LoggerFactory.getLogger(ClientBrieferService.class);
    private static final int JSOUP_TIMEOUT_MS = 10_000;
    private static final int MAX_TEXT_CHARS = 5_000;
    private static final String MODULE = "CLIENT_BRIEFER";

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;
    private final ClientRepository clientRepo;
    private final ClientProfileRepository profileRepo;
    private final ClientProfileService profileService;
    private final ObjectMapper objectMapper;

    public ClientBrieferService(ClaudeApiClient claudeClient,
                                 AiProperties aiProps,
                                 ClientRepository clientRepo,
                                 ClientProfileRepository profileRepo,
                                 ClientProfileService profileService,
                                 ObjectMapper objectMapper) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.clientRepo = clientRepo;
        this.profileRepo = profileRepo;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
    }

    /**
     * Analyse the client's website and update their profile.
     *
     * @return the AI analysis as a map (same data stored in profile_json)
     */
    @Transactional
    public Map<String, Object> analyzeWebsite(UUID agencyId, UUID clientId, String websiteUrl) {
        Client client = clientRepo.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        // 1. Fetch website text via Jsoup
        String siteText = fetchWebsiteText(websiteUrl);

        // 2. Build context for Claude
        StringBuilder ctx = new StringBuilder();
        ctx.append("WEBSITE URL: ").append(websiteUrl).append("\n");
        ctx.append("CLIENT NAME: ").append(client.getName()).append("\n");
        if (client.getIndustry() != null) {
            ctx.append("EXISTING INDUSTRY TAG: ").append(client.getIndustry()).append("\n");
        }
        ctx.append("\n");

        if (siteText != null && !siteText.isBlank()) {
            ctx.append("=== EXTRACTED WEBSITE TEXT (first ").append(MAX_TEXT_CHARS).append(" chars) ===\n");
            ctx.append(siteText).append("\n");
        } else {
            ctx.append("NOTE: Could not scrape website text. Analyse based on the URL alone.\n");
        }

        // 3. Call Claude
        String systemPrompt = """
                You are a marketing strategist analysing a business website.
                Based on the provided website text (or URL if text is unavailable), return a JSON object with:
                {
                  "industry": "string — specific industry/niche",
                  "business_model": "ECOM | SERVICE | B2B | LOCAL | LUXURY | SAAS | OTHER",
                  "usp": "string — unique selling proposition (1-2 sentences)",
                  "target_audiences": ["audience 1", "audience 2", ...],
                  "tone_of_voice": "string — brand tone description",
                  "offers": ["product/service 1", "product/service 2", ...],
                  "competitors": ["competitor 1", ...],
                  "suggested_strategy": "string — 2-3 sentence Meta Ads strategy recommendation",
                  "suggested_monthly_budget_range": "string — e.g. '$2,000 - $5,000'",
                  "brand_colors": "string — if detectable from the site, else null",
                  "languages": ["string — detected content languages"]
                }
                
                Respond with STRICT JSON only, no markdown fences, no extra text.
                If you cannot determine a field, use null or an empty array.
                """;

        ClaudeResponse response = claudeClient.sendMessage(
                systemPrompt, ctx.toString(),
                MODULE, agencyId, clientId);

        if (!response.isSuccess()) {
            log.warn("Client Briefer: Claude call failed for client {}: {}", clientId, response.error());
            return Map.of("error", "AI analysis failed: " + response.error(),
                          "websiteUrl", websiteUrl);
        }

        // 4. Parse response
        JsonNode json = claudeClient.parseJson(response.text());
        if (json == null) {
            log.warn("Client Briefer: Failed to parse Claude response for client {}", clientId);
            return Map.of("error", "Failed to parse AI response",
                          "websiteUrl", websiteUrl);
        }

        Map<String, Object> analysis;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.treeToValue(json, Map.class);
            analysis = new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            log.warn("Client Briefer: JSON conversion failed: {}", e.getMessage());
            analysis = Map.of("error", "JSON conversion failed",
                              "raw", response.text());
        }

        // 5. Update client industry if we got a useful one
        String aiIndustry = json.has("industry") && !json.get("industry").isNull()
                ? json.get("industry").asText() : null;
        if (aiIndustry != null && !aiIndustry.isBlank() && client.getIndustry() == null) {
            client.setIndustry(aiIndustry);
            clientRepo.save(client);
        }

        // 6. Update or create client profile via service
        profileService.upsertProfile(agencyId, clientId,
                new ClientProfileRequest(websiteUrl, analysis));

        log.info("Client Briefer: analysed website {} for client {}", websiteUrl, clientId);
        return analysis;
    }

    /**
     * Fetch last analysis result (from stored profile).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLastAnalysis(UUID agencyId, UUID clientId) {
        clientRepo.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        ClientProfile profile = profileRepo.findByClientId(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientProfile", clientId));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(profile.getProfileJson(), Map.class);
            Map<String, Object> result = new LinkedHashMap<>(parsed);
            result.put("websiteUrl", profile.getWebsite());
            result.put("updatedAt", profile.getUpdatedAt());
            return result;
        } catch (Exception e) {
            return Map.of("error", "Could not parse stored profile",
                          "websiteUrl", profile.getWebsite());
        }
    }

    // ──────── Jsoup website fetch ────────

    private String fetchWebsiteText(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(JSOUP_TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (compatible; AMP-Bot/1.0)")
                    .followRedirects(true)
                    .get();

            // Remove script/style elements before extracting text
            doc.select("script, style, noscript, iframe").remove();
            String text = doc.body() != null ? doc.body().text() : doc.text();

            // Trim to max chars
            if (text.length() > MAX_TEXT_CHARS) {
                text = text.substring(0, MAX_TEXT_CHARS);
            }
            return text;
        } catch (Exception e) {
            log.warn("Client Briefer: Jsoup fetch failed for {}: {}", url, e.getMessage());
            return null;
        }
    }
}
