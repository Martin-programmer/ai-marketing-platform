package com.amp.meta;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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
                            + "impressions,clicks,spend,cpc,cpm,ctr,actions,conversions,cost_per_action_type")
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

    public record PageInfo(String id, String name) {}
}
