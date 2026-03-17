package com.amp.meta;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
        String locationTypes = locationType != null && !locationType.isBlank()
                ? "[\"" + locationType + "\"]"
                : "[\"country\",\"city\",\"region\"]";

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

    /**
     * Update an adset's daily budget (in currency's smallest unit, e.g. cents).
     */
    public JsonNode updateAdsetBudget(String accessToken, String adsetId, long dailyBudgetCents) {
        return updateEntityField(accessToken, adsetId, "daily_budget", String.valueOf(dailyBudgetCents));
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
     */
    public JsonNode createCampaign(String accessToken, String adAccountId,
                                   String name, String objective, String status) {
        String url = metaProps.getGraphUrl(adAccountId + "/campaigns");
        String body = "access_token=" + accessToken
                + "&name=" + urlEncode(name)
                + "&objective=" + objective
                + "&status=" + status
                + "&special_ad_categories=[]";
        return postForm(url, body);
    }

    /**
     * Create an adset in Meta.
     */
    public JsonNode createAdset(String accessToken, String metaCampaignId,
                                String name, long dailyBudgetCents,
                                String targetingJson, String optimizationGoal,
                                String billingEvent, String status,
                                String startTimeIso) {
        String url = metaProps.getGraphUrl(metaCampaignId + "/adsets");
        StringBuilder body = new StringBuilder();
        body.append("access_token=").append(accessToken)
            .append("&name=").append(urlEncode(name))
            .append("&daily_budget=").append(dailyBudgetCents)
            .append("&targeting=").append(urlEncode(targetingJson))
            .append("&optimization_goal=").append(optimizationGoal)
            .append("&billing_event=").append(billingEvent)
            .append("&status=").append(status != null ? status : "PAUSED");
        if (startTimeIso != null && !startTimeIso.isBlank()) {
            body.append("&start_time=").append(urlEncode(startTimeIso));
        }
        return postForm(url, body.toString());
    }

    /**
     * Backward-compatible overload (defaults to PAUSED, no start_time).
     */
    public JsonNode createAdset(String accessToken, String metaCampaignId,
                                String name, long dailyBudgetCents,
                                String targetingJson, String optimizationGoal,
                                String billingEvent) {
        return createAdset(accessToken, metaCampaignId, name, dailyBudgetCents,
                targetingJson, optimizationGoal, billingEvent, "PAUSED", null);
    }

    /**
     * Create an ad in Meta.
     */
    public JsonNode createAd(String accessToken, String metaAdsetId,
                             String name, String creativeId, String status) {
        String url = metaProps.getGraphUrl(metaAdsetId + "/ads");
        // creative must be a JSON object: {"creative_id":"..."}
        String creativeJson = "{\"creative_id\":\"" + creativeId + "\"}";
        String body = "access_token=" + accessToken
                + "&name=" + urlEncode(name)
                + "&creative=" + urlEncode(creativeJson)
                + "&status=" + (status != null ? status : "PAUSED");
        return postForm(url, body);
    }

    /**
     * Backward-compatible overload: creates ad using raw creative spec string (PAUSED).
     */
    public JsonNode createAd(String accessToken, String metaAdsetId,
                             String name, String creativeSpec) {
        String url = metaProps.getGraphUrl(metaAdsetId + "/ads");
        String body = "access_token=" + accessToken
                + "&name=" + urlEncode(name)
                + "&creative=" + urlEncode(creativeSpec)
                + "&status=PAUSED";
        return postForm(url, body);
    }

    /**
     * Upload an image to a Meta ad account.
     * POST /{adAccountId}/adimages  (multipart/form-data with "filename" part)
     * Returns the full response; caller should parse image hash from it.
     */
    public JsonNode uploadImage(String accessToken, String adAccountId, byte[] imageBytes, String filename) {
        String url = metaProps.getGraphUrl(adAccountId + "/adimages");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        org.springframework.util.LinkedMultiValueMap<String, Object> parts = new org.springframework.util.LinkedMultiValueMap<>();
        parts.add("access_token", accessToken);

        // Wrap bytes in a ByteArrayResource with a filename so Spring sends it as a file part
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename != null ? filename : "image.jpg";
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<org.springframework.core.io.ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);
        parts.add("filename", filePart);

        HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> entity = new HttpEntity<>(parts, headers);
        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(url, entity, JsonNode.class);
        return resp.getBody();
    }

    /**
     * Create an ad creative in Meta with proper object_story_spec for image link ads.
     * POST /{adAccountId}/adcreatives
     */
    public JsonNode createAdCreative(String accessToken, String adAccountId,
                                     String name, String imageHash, String pageId,
                                     String primaryText, String headline,
                                     String description, String ctaType, String link) {
        // Build the object_story_spec JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode spec = mapper.createObjectNode();
        spec.put("page_id", pageId);

        com.fasterxml.jackson.databind.node.ObjectNode linkData = mapper.createObjectNode();
        if (imageHash != null) {
            linkData.put("image_hash", imageHash);
        }
        linkData.put("message", primaryText != null ? primaryText : "");
        linkData.put("name", headline != null ? headline : "");
        if (description != null && !description.isBlank()) {
            linkData.put("description", description);
        }
        linkData.put("link", link != null ? link : "");

        if (ctaType != null && !ctaType.isBlank()) {
            com.fasterxml.jackson.databind.node.ObjectNode cta = mapper.createObjectNode();
            cta.put("type", ctaType);
            com.fasterxml.jackson.databind.node.ObjectNode ctaValue = mapper.createObjectNode();
            ctaValue.put("link", link != null ? link : "");
            cta.set("value", ctaValue);
            linkData.set("call_to_action", cta);
        }

        spec.set("link_data", linkData);

        String url = metaProps.getGraphUrl(adAccountId + "/adcreatives");
        String body = "access_token=" + accessToken
                + "&name=" + urlEncode(name)
                + "&object_story_spec=" + urlEncode(spec.toString());
        return postForm(url, body);
    }

    private JsonNode postForm(String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(url, entity, JsonNode.class);
        return resp.getBody();
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private JsonNode updateEntityField(String accessToken, String entityId,
                                       String field, String value) {
        String url = metaProps.getGraphUrl(entityId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "access_token=" + accessToken + "&" + field + "=" + value;

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(url, entity, JsonNode.class);
        return resp.getBody();
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
