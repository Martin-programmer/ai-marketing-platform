package com.amp.meta;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * REST client that talks to the Meta Graph API.
 * Handles token exchange, ad account listing, campaigns, adsets, ads,
 * insights, and page listing — with cursor-based pagination.
 */
@Component
public class MetaGraphApiClient {

    private static final Logger log = LoggerFactory.getLogger(MetaGraphApiClient.class);

    private final MetaProperties metaProps;
    private final RestTemplate restTemplate;

    public MetaGraphApiClient(MetaProperties metaProps) {
        this.metaProps = metaProps;
        this.restTemplate = new RestTemplate();
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().setAcceptCharset(List.of(StandardCharsets.UTF_8));
            return execution.execute(request, body);
        });
    }

    /**
     * Exchange authorization code for access token.
     */
    public TokenExchangeResult exchangeCodeForToken(String code) {
        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphUrl("oauth/access_token"))
                .queryParam("client_id", metaProps.getAppId())
                .queryParam("client_secret", metaProps.getAppSecret())
                .queryParam("redirect_uri", metaProps.getRedirectUri())
                .queryParam("code", code)
                .toUriString();

        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
        JsonNode body = resp.getBody();

        String accessToken = body.get("access_token").asText();
        long expiresIn = body.has("expires_in") ? body.get("expires_in").asLong() : 0;

        return new TokenExchangeResult(accessToken, expiresIn);
    }

    /**
     * Exchange short-lived token for long-lived token (60 days).
     */
    public TokenExchangeResult exchangeForLongLivedToken(String shortLivedToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphUrl("oauth/access_token"))
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", metaProps.getAppId())
                .queryParam("client_secret", metaProps.getAppSecret())
                .queryParam("fb_exchange_token", shortLivedToken)
                .toUriString();

        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
        JsonNode body = resp.getBody();

        String accessToken = body.get("access_token").asText();
        long expiresIn = body.has("expires_in") ? body.get("expires_in").asLong() : 5184000; // default 60 days

        return new TokenExchangeResult(accessToken, expiresIn);
    }

    /**
     * Get list of ad accounts the user has access to.
     */
    public List<AdAccountInfo> getAdAccounts(String accessToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphUrl("me/adaccounts"))
                .queryParam("access_token", accessToken)
                .queryParam("fields", "id,name,account_id,account_status,currency,timezone_name")
                .queryParam("limit", 100)
                .toUriString();

        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
        JsonNode data = resp.getBody().get("data");

        List<AdAccountInfo> accounts = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                accounts.add(new AdAccountInfo(
                        node.get("id").asText(),
                        node.has("name") ? node.get("name").asText() : "",
                        node.has("account_id") ? node.get("account_id").asText() : "",
                        node.has("account_status") ? node.get("account_status").asInt() : 0,
                        node.has("currency") ? node.get("currency").asText() : "",
                        node.has("timezone_name") ? node.get("timezone_name").asText() : ""
                ));
            }
        }
        return accounts;
    }

    /**
     * Fetch campaigns for an ad account.
     */
    public List<JsonNode> getCampaigns(String accessToken, String adAccountId) {
        return fetchPaginated(
                metaProps.getGraphUrl(adAccountId + "/campaigns"),
                accessToken,
                "id,name,objective,status,daily_budget,lifetime_budget,start_time,stop_time,created_time,updated_time"
        );
    }

    /**
     * Fetch adsets for an ad account.
     */
    public List<JsonNode> getAdSets(String accessToken, String adAccountId) {
        return fetchPaginated(
                metaProps.getGraphUrl(adAccountId + "/adsets"),
                accessToken,
                "id,name,campaign_id,status,daily_budget,lifetime_budget,targeting,optimization_goal,billing_event,start_time,end_time,created_time,updated_time"
        );
    }

    /**
     * Fetch ads for an ad account.
     */
    public List<JsonNode> getAds(String accessToken, String adAccountId) {
        return fetchPaginated(
                metaProps.getGraphUrl(adAccountId + "/ads"),
                accessToken,
                "id,name,adset_id,campaign_id,status,creative,created_time,updated_time"
        );
    }

    /**
     * Fetch insights (performance data) for an ad account.
     * Returns daily breakdowns for the specified date range.
     * Splits large ranges into 30-day chunks to avoid cursor overflow errors.
     */
    public List<JsonNode> getAccountInsights(String accessToken, String adAccountId,
                                             String dateFrom, String dateTo) {
        List<JsonNode> allResults = new ArrayList<>();

        // Split into monthly chunks to avoid cursor issues
        LocalDate start = LocalDate.parse(dateFrom);
        LocalDate end = LocalDate.parse(dateTo);

        while (start.isBefore(end)) {
            LocalDate chunkEnd = start.plusDays(30);
            if (chunkEnd.isAfter(end)) {
                chunkEnd = end;
            }

            String url = UriComponentsBuilder
                    .fromHttpUrl(metaProps.getGraphUrl(adAccountId + "/insights"))
                    .queryParam("access_token", accessToken)
                    .queryParam("fields", "campaign_id,campaign_name,adset_id,adset_name,ad_id,ad_name,"
                            + "impressions,clicks,spend,cpc,cpm,ctr,frequency,reach,actions,action_values,cost_per_action_type")
                    .queryParam("time_range[since]", start.toString())
                    .queryParam("time_range[until]", chunkEnd.toString())
                    .queryParam("time_increment", "1")
                    .queryParam("level", "ad")
                    .queryParam("limit", 500)
                    .build(false)
                    .toUriString();

            try {
                List<JsonNode> chunkResults = fetchPaginatedRaw(url);
                allResults.addAll(chunkResults);
                log.info("Fetched {} insight records for period {} to {}", chunkResults.size(), start, chunkEnd);
            } catch (Exception e) {
                log.warn("Failed to fetch insights for period {} to {}: {}", start, chunkEnd, e.getMessage());
                // Continue with next chunk instead of failing entirely
            }

            start = chunkEnd.plusDays(1);
        }

        return allResults;
    }

    /**
     * Get user's Facebook pages.
     */
    public List<PageInfo> getPages(String accessToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphUrl("me/accounts"))
                .queryParam("access_token", accessToken)
                .queryParam("fields", "id,name")
                .queryParam("limit", 100)
                .toUriString();

        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
        JsonNode data = resp.getBody().get("data");

        List<PageInfo> pages = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                pages.add(new PageInfo(node.get("id").asText(), node.get("name").asText()));
            }
        }
        return pages;
    }

    /**
     * Get pixels available for an ad account.
     */
    public List<PixelInfo> getAdAccountPixels(String accessToken, String adAccountId) {
        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphUrl(adAccountId + "/adspixels"))
                .queryParam("access_token", accessToken)
                .queryParam("fields", "id,name")
                .queryParam("limit", 100)
                .toUriString();

        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
        JsonNode data = resp.getBody().get("data");

        List<PixelInfo> pixels = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                pixels.add(new PixelInfo(
                        node.get("id").asText(),
                        node.has("name") ? node.get("name").asText() : ""
                ));
            }
        }
        return pixels;
    }

    // ── Search / Targeting methods ────────────────────────

    /**
     * Search ad interests via Meta's targeting search API.
     * GET /search?type=adinterest&q={query}
     */
    public List<JsonNode> searchInterests(String accessToken, String query) {
        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphUrl("search"))
                .queryParam("access_token", accessToken)
                .queryParam("type", "adinterest")
                .queryParam("q", query)
                .queryParam("limit", 25)
                .build(false)
                .toUriString();

        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
        JsonNode data = resp.getBody().get("data");
        List<JsonNode> results = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                results.add(node);
            }
        }
        return results;
    }

    /**
     * Search geo locations for ad targeting.
     * GET /search?type=adgeolocation&q={query}&location_types=["{locationType}"]
     */
    public List<JsonNode> searchGeoLocations(String accessToken, String query, String locationType) {
        String locationTypes;
        if (locationType != null && !locationType.isBlank()) {
            String[] rawTypes = locationType.split(",");
            List<String> normalizedTypes = new ArrayList<>();
            for (String rawType : rawTypes) {
                if (rawType != null && !rawType.isBlank()) {
                    normalizedTypes.add(rawType.trim());
                }
            }
            if (normalizedTypes.isEmpty()) {
                locationTypes = "[\"country\",\"city\",\"region\"]";
            } else {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < normalizedTypes.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append('"').append(normalizedTypes.get(i)).append('"');
                }
                sb.append(']');
                locationTypes = sb.toString();
            }
        } else {
            locationTypes = "[\"country\",\"city\",\"region\"]";
        }

        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphUrl("search"))
                .queryParam("access_token", accessToken)
                .queryParam("type", "adgeolocation")
                .queryParam("q", query)
                .queryParam("location_types", locationTypes)
                .queryParam("limit", 25)
                .build(false)
                .toUriString();

        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
        JsonNode data = resp.getBody().get("data");
        List<JsonNode> results = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                results.add(node);
            }
        }
        return results;
    }

    /**
     * Get custom audiences for an ad account.
     * GET /{ad_account_id}/customaudiences?fields=id,name,approximate_count
     */
    public List<JsonNode> getCustomAudiences(String accessToken, String adAccountId) {
        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphUrl(adAccountId + "/customaudiences"))
                .queryParam("access_token", accessToken)
                .queryParam("fields", "id,name,approximate_count")
                .queryParam("limit", 200)
                .toUriString();

        return fetchPaginatedRaw(url);
    }

    // ── Write methods ───────────────────────────────────────

    /**
     * Update an ad's status (pause/enable).
     */
    public JsonNode updateAdStatus(String accessToken, String adId, String status) {
        return updateEntityField(accessToken, adId, "status", status);
    }

    /**
     * Update an adset's status.
     */
    public JsonNode updateAdsetStatus(String accessToken, String adsetId, String status) {
        return updateEntityField(accessToken, adsetId, "status", status);
    }

    /**
     * Update a campaign's status.
     */
    public JsonNode updateCampaignStatus(String accessToken, String campaignId, String status) {
        return updateEntityField(accessToken, campaignId, "status", status);
    }

    public JsonNode updateCampaign(String accessToken, String campaignId, String name, String status) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("access_token", accessToken);
        if (name != null && !name.isBlank()) {
            params.add("name", name);
        }
        if (status != null && !status.isBlank()) {
            params.add("status", status);
        }
        return updateEntityFields(accessToken, campaignId, params);
    }

    /**
     * Update an adset's daily budget (in currency's smallest unit, e.g. cents).
     */
    public JsonNode updateAdsetBudget(String accessToken, String adsetId, long dailyBudgetCents) {
        return updateEntityField(accessToken, adsetId, "daily_budget", String.valueOf(dailyBudgetCents));
    }

    public JsonNode updateAdset(String accessToken, String adsetId, String name,
                                Long dailyBudgetCents, String targetingJson,
                                String optimizationGoal, String promotedObjectJson,
                                String status) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("access_token", accessToken);
        if (name != null && !name.isBlank()) params.add("name", name);
        if (dailyBudgetCents != null) params.add("daily_budget", String.valueOf(dailyBudgetCents));
        if (targetingJson != null && !targetingJson.isBlank()) params.add("targeting", targetingJson);
        if (optimizationGoal != null && !optimizationGoal.isBlank()) params.add("optimization_goal", optimizationGoal);
        if (promotedObjectJson != null && !promotedObjectJson.isBlank()) params.add("promoted_object", promotedObjectJson);
        if (status != null && !status.isBlank()) params.add("status", status);
        return updateEntityFields(accessToken, adsetId, params);
    }

    public JsonNode updateAd(String accessToken, String adId, String name, String creativeJson, String status) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("access_token", accessToken);
        if (name != null && !name.isBlank()) params.add("name", name);
        if (creativeJson != null && !creativeJson.isBlank()) params.add("creative", creativeJson);
        if (status != null && !status.isBlank()) params.add("status", status);
        return updateEntityFields(accessToken, adId, params);
    }

    /**
     * Get current entity details from Meta.
     */
    public JsonNode getEntityDetails(String accessToken, String entityId, String fields) {
        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphUrl(entityId))
                .queryParam("access_token", accessToken)
                .queryParam("fields", fields)
                .toUriString();

        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
        return resp.getBody();
    }

    /**
     * Create a campaign in Meta.
     * Uses form-urlencoded. For CBO campaigns, sets campaign_budget_optimization + daily_budget.
     */
    public JsonNode createCampaign(String accessToken, String adAccountId,
                                   String name, String objective, String status,
                                   String budgetType, BigDecimal dailyBudget) {
        String url = metaProps.getGraphUrl(adAccountId + "/campaigns");
        log.info("Meta API: POST {} — name={}, objective={}, budgetType={}", url, name, objective, budgetType);

        String resolvedBudgetType = "CBO".equalsIgnoreCase(budgetType) ? "CBO" : "ABO";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("access_token", accessToken);
        params.add("name", name);
        params.add("objective", objective);
        params.add("status", status != null ? status : "PAUSED");
        params.add("special_ad_categories", "[]");

        if ("CBO".equals(resolvedBudgetType) && dailyBudget != null) {
            long dailyBudgetCents = dailyBudget.multiply(BigDecimal.valueOf(100)).longValue();
            params.add("campaign_budget_optimization", "true");
            params.add("daily_budget", String.valueOf(dailyBudgetCents));
        } else {
            params.add("is_adset_budget_sharing_enabled", "false");
        }

        JsonNode result = postFormParams(url, params);
        log.info("Meta API success: created CAMPAIGN with ID {}", result.get("id"));
        return result;
    }

    /**
     * Create an adset in Meta.
     * Uses form-urlencoded. Includes bid_strategy, billing_event, start_time.
     * For CBO campaigns, omit daily_budget on the adset.
     *
     * @param token            Meta access token
     * @param adAccountId      ad account ID (act_XXXXXX)
     * @param campaignId       Meta campaign ID
     * @param name             adset name
     * @param dailyBudgetCents daily budget in cents (null for CBO campaigns)
     * @param targetingJson    targeting JSON already in Meta format
     * @param optimizationGoal e.g. LINK_CLICKS, IMPRESSIONS, REACH, OFFSITE_CONVERSIONS
         * @param promotedObjectJson Meta promoted_object JSON, optional
         * @param startTimeUnix    unix timestamp seconds as string
         * @param endTimeUnix      unix timestamp seconds as string, optional
     * @param status           e.g. PAUSED, ACTIVE
     * @param isCBO            true if campaign uses CBO (skip adset-level budget)
     */
    public JsonNode createAdset(String token, String adAccountId, String campaignId,
                                String name, Long dailyBudgetCents,
                                String targetingJson, String optimizationGoal,
                          String promotedObjectJson, String startTimeUnix, String endTimeUnix,
                                String status, boolean isCBO) {
        String url = metaProps.getGraphUrl(adAccountId + "/adsets");
        log.info("Meta API: POST {} — name={}, campaignId={}, isCBO={}", url, name, campaignId, isCBO);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("access_token", token);
        params.add("campaign_id", campaignId);
        params.add("name", name);

        if (!isCBO && dailyBudgetCents != null) {
            params.add("daily_budget", String.valueOf(dailyBudgetCents));
        }

        params.add("optimization_goal", optimizationGoal != null ? optimizationGoal : "LINK_CLICKS");
        params.add("billing_event", "IMPRESSIONS");
        params.add("targeting", targetingJson);
        params.add("status", status != null ? status : "PAUSED");
        params.add("bid_strategy", "LOWEST_COST_WITHOUT_CAP");
        if (promotedObjectJson != null && !promotedObjectJson.isBlank()) {
            params.add("promoted_object", promotedObjectJson);
        }
        if (startTimeUnix != null && !startTimeUnix.isBlank()) {
            params.add("start_time", startTimeUnix);
        }
        if (endTimeUnix != null && !endTimeUnix.isBlank()) {
            params.add("end_time", endTimeUnix);
        }

        JsonNode result = postFormParams(url, params);
        log.info("Meta API success: created ADSET with ID {}", result.get("id"));
        return result;
    }

    /**
     * Upload an image to a Meta ad account.
     * POST /{adAccountId}/adimages  (multipart/form-data with "filename" part)
     * Returns the image hash string directly.
     */
    public String uploadImage(String accessToken, String adAccountId, byte[] imageBytes, String filename) {
        String url = metaProps.getGraphUrl(adAccountId + "/adimages");
        log.info("Meta API: POST {} — filename={}, size={}bytes", url, filename, imageBytes != null ? imageBytes.length : 0);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("access_token", accessToken);

        ByteArrayResource resource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename != null ? filename : "image.jpg";
            }
        };
        body.add("filename", resource);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(url, entity, JsonNode.class);
        JsonNode responseBody = resp.getBody();

        // Parse image hash from response: {"images":{"filename.jpg":{"hash":"abc123"}}}
        if (responseBody != null && responseBody.has("images")) {
            JsonNode images = responseBody.get("images");
            Iterator<JsonNode> it = images.elements();
            if (it.hasNext()) {
                JsonNode imageInfo = it.next();
                if (imageInfo.has("hash")) {
                    String hash = imageInfo.get("hash").asText();
                    log.info("Meta API success: uploaded IMAGE, hash={}", hash);
                    return hash;
                }
            }
        }

        log.error("Meta API failed: could not parse image hash from response: {}", responseBody);
        throw new IllegalStateException("Failed to parse image hash from Meta response");
    }

    /**
     * Create an ad creative in Meta with proper object_story_spec for image link ads.
     * POST /{adAccountId}/adcreatives
     *
     * @param accessToken Meta access token
     * @param adAccountId ad account ID (act_XXXXXX)
     * @param name        creative name
     * @param imageHash   the image hash from uploadImage (nullable for no-image ads)
     * @param pageId      Facebook Page ID (REQUIRED)
     * @param message     primary text / message
     * @param headline    headline text
     * @param description description text
     * @param ctaType     CTA type string (e.g. LEARN_MORE, SHOP_NOW)
     * @param link        destination URL
     */
    public JsonNode createAdCreative(String accessToken, String adAccountId,
                                     String name, String imageHash, String pageId,
                                     String message, String headline,
                                     String description, String ctaType, String link) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalStateException("page_id is REQUIRED for ad creatives. Please link a Facebook Page.");
        }

        String url = metaProps.getGraphUrl(adAccountId + "/adcreatives");
        log.info("Meta API: POST {} — name={}, pageId={}, hasImage={}", url, name, pageId, imageHash != null);

        // Build the object_story_spec JSON
        ObjectNode storySpec = new ObjectMapper().createObjectNode();
        storySpec.put("page_id", pageId);

        ObjectNode linkData = storySpec.putObject("link_data");
        linkData.put("message", message != null ? message : "");
        linkData.put("name", headline != null ? headline : "");
        if (description != null && !description.isBlank()) {
            linkData.put("description", description);
        }
        linkData.put("link", link != null && !link.isBlank() ? link : "https://example.com");

        if (imageHash != null && !imageHash.isBlank()) {
            linkData.put("image_hash", imageHash);
        }

        String resolvedCta = mapCtaType(ctaType);
        if (resolvedCta != null) {
            ObjectNode cta = linkData.putObject("call_to_action");
            cta.put("type", resolvedCta);
            ObjectNode ctaValue = cta.putObject("value");
            ctaValue.put("link", link != null && !link.isBlank() ? link : "https://example.com");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("access_token", accessToken);
        params.add("name", name);
        params.add("object_story_spec", storySpec.toString());

        JsonNode result = postFormParams(url, params);
        log.info("Meta API success: created AD_CREATIVE with ID {}", result.get("id"));
        return result;
    }

    /**
     * Create an ad in Meta referencing a creative.
     * POST /{adAccountId}/ads  (form-urlencoded)
     */
    public JsonNode createAd(String token, String adAccountId, String adsetId,
                             String name, String creativeId, String status) {
        String url = metaProps.getGraphUrl(adAccountId + "/ads");
        log.info("Meta API: POST {} — name={}, adsetId={}, creativeId={}", url, name, adsetId, creativeId);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("access_token", token);
        params.add("adset_id", adsetId);
        params.add("name", name);
        params.add("status", status != null ? status : "PAUSED");
        params.add("creative", "{\"creative_id\":\"" + creativeId + "\"}");

        JsonNode result = postFormParams(url, params);
        log.info("Meta API success: created AD with ID {}", result.get("id"));
        return result;
    }

    // ── CTA type mapping ────────────────────────────────────

    private static final Map<String, String> CTA_TYPE_MAP = Map.ofEntries(
            Map.entry("SHOP_NOW", "SHOP_NOW"),
            Map.entry("LEARN_MORE", "LEARN_MORE"),
            Map.entry("SIGN_UP", "SIGN_UP"),
            Map.entry("BOOK_NOW", "BOOK_TRAVEL"),
            Map.entry("CONTACT_US", "CONTACT_US"),
            Map.entry("GET_OFFER", "GET_OFFER"),
            Map.entry("SUBSCRIBE", "SUBSCRIBE"),
            Map.entry("DOWNLOAD", "DOWNLOAD"),
            Map.entry("BUY_NOW", "SHOP_NOW"),
            Map.entry("APPLY_NOW", "APPLY_NOW"),
            Map.entry("GET_QUOTE", "GET_QUOTE"),
            Map.entry("ORDER_NOW", "ORDER_NOW"),
            Map.entry("WATCH_MORE", "WATCH_MORE")
    );

    /**
     * Map our CTA type string to a Meta-recognized CTA type.
     */
    private static String mapCtaType(String ctaType) {
        if (ctaType == null || ctaType.isBlank()) return null;
        String upper = ctaType.trim().toUpperCase();
        return CTA_TYPE_MAP.getOrDefault(upper, upper);
    }

    // ── Form POST helper (MultiValueMap) ────────────────────

    private JsonNode postFormParams(String url, MultiValueMap<String, String> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
        try {
            ResponseEntity<JsonNode> resp = restTemplate.postForEntity(url, entity, JsonNode.class);
            return resp.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Meta API failed: POST {} — status={}, body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    private JsonNode updateEntityField(String accessToken, String entityId,
                                       String field, String value) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("access_token", accessToken);
        params.add(field, value);

        return updateEntityFields(accessToken, entityId, params);
    }

    private JsonNode updateEntityFields(String accessToken, String entityId,
                                        MultiValueMap<String, String> params) {
        String url = metaProps.getGraphUrl(entityId);
        log.info("Meta API: POST {} — fields={}", url, params.keySet());

        return postFormParams(url, params);
    }

    // ── Pagination helpers ──────────────────────────────────

    private List<JsonNode> fetchPaginated(String baseUrl, String accessToken, String fields) {
        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .queryParam("access_token", accessToken)
                .queryParam("fields", fields)
                .queryParam("limit", 200)
                .toUriString();

        return fetchPaginatedRaw(url);
    }

    private List<JsonNode> fetchPaginatedRaw(String url) {
        List<JsonNode> allResults = new ArrayList<>();
        int maxPages = 20; // safety limit

        for (int page = 0; page < maxPages && url != null; page++) {
            try {
                ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
                JsonNode body = resp.getBody();
                JsonNode data = body.get("data");

                if (data != null && data.isArray()) {
                    for (JsonNode node : data) {
                        allResults.add(node);
                    }
                }

                // Next page
                url = null;
                if (body.has("paging") && body.get("paging").has("next")) {
                    url = body.get("paging").get("next").asText();
                }
            } catch (Exception e) {
                log.warn("Pagination error on page {}, returning {} results collected so far: {}",
                        page, allResults.size(), e.getMessage());
                break; // Return what we have so far
            }
        }
        return allResults;
    }

    // ── DTOs ────────────────────────────────────────────────

    public record TokenExchangeResult(String accessToken, long expiresInSeconds) {}

    public record AdAccountInfo(String id, String name, String accountId,
                                int status, String currency, String timezone) {}

    public static boolean isSelectableAdAccount(AdAccountInfo account) {
        return account != null && account.status() == 1;
    }

    public static String adAccountStatusLabel(int status) {
        return status == 1 ? "ACTIVE" : "DISABLED";
    }

    public record PixelInfo(String id, String name) {}

    public record PageInfo(String id, String name) {}
}
