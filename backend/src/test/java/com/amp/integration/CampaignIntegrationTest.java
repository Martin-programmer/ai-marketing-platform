package com.amp.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Campaign / Adset / Ad API – Integration Tests")
class CampaignIntegrationTest extends BaseIntegrationTest {

    // ── helpers ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String createTestClient() {
        Map<String, Object> body = Map.of(
                "name", "Camp Client " + UUID.randomUUID(),
                "industry", "RETAIL"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private String createCampaign(String clientId) {
        Map<String, Object> body = Map.of(
                "clientId", clientId,
                "name", "Campaign " + UUID.randomUUID(),
                "objective", "SALES"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients/" + clientId + "/campaigns", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private String createAdset(String campaignId) {
        Map<String, Object> body = Map.of(
                "name", "Adset " + UUID.randomUUID(),
                "dailyBudget", 50.00,
                "targetingJson", "{\"age_min\": 25, \"age_max\": 55}"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/campaigns/" + campaignId + "/adsets", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().get("id").toString();
    }

    // ── tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST + GET — create campaign in DRAFT, then list")
    @SuppressWarnings("unchecked")
    void createAndGetCampaign() {
        String clientId = createTestClient();

        Map<String, Object> body = Map.of(
                "clientId", clientId,
                "name", "Test Camp " + UUID.randomUUID(),
                "objective", "AWARENESS"
        );
        ResponseEntity<Map> create = restTemplate.exchange(
                "/api/v1/clients/" + clientId + "/campaigns", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);

        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> camp = create.getBody();
        assertThat(camp.get("status")).isEqualTo("DRAFT");
        assertThat(camp.get("metaCampaignId")).isNull();

        // LIST
        ResponseEntity<List> list = restTemplate.exchange(
                "/api/v1/clients/" + clientId + "/campaigns", HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), List.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Lifecycle: DRAFT → PUBLISHED → PAUSED → PUBLISHED")
    @SuppressWarnings("unchecked")
    void campaignLifecycle() {
        String clientId = createTestClient();
        String campId = createCampaign(clientId);

        // PUBLISH
        ResponseEntity<Map> publish = restTemplate.exchange(
                "/api/v1/campaigns/" + campId + "/publish", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);
        assertThat(publish.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publish.getBody().get("status")).isEqualTo("PUBLISHED");

        // PAUSE
        ResponseEntity<Map> pause = restTemplate.exchange(
                "/api/v1/campaigns/" + campId + "/pause", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);
        assertThat(pause.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pause.getBody().get("status")).isEqualTo("PAUSED");

        // RESUME (back to PUBLISHED)
        ResponseEntity<Map> resume = restTemplate.exchange(
                "/api/v1/campaigns/" + campId + "/resume", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);
        assertThat(resume.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resume.getBody().get("status")).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("POST adset — budget and targeting persisted")
    @SuppressWarnings("unchecked")
    void createAdsetUnderCampaign() {
        String clientId = createTestClient();
        String campId = createCampaign(clientId);

        Map<String, Object> body = Map.of(
                "name", "Budget Adset " + UUID.randomUUID(),
                "dailyBudget", 100.50,
                "targetingJson", "{\"genders\": [\"male\"]}"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/campaigns/" + campId + "/adsets", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> adset = r.getBody();
        assertThat(adset.get("campaignId")).isEqualTo(campId);
        Number budget = (Number) adset.get("dailyBudget");
        assertThat(budget.doubleValue()).isEqualTo(100.50);
        assertThat(adset.get("targetingJson")).isNotNull();
    }

    @Test
    @DisplayName("POST ad under adset — linked correctly")
    @SuppressWarnings("unchecked")
    void createAdUnderAdset() {
        String clientId = createTestClient();
        String campId = createCampaign(clientId);
        String adsetId = createAdset(campId);

        Map<String, Object> body = Map.of(
                "name", "Ad " + UUID.randomUUID()
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/adsets/" + adsetId + "/ads", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> ad = r.getBody();
        assertThat(ad.get("adsetId")).isEqualTo(adsetId);
        assertThat(ad.get("status")).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("GET campaigns for new client — empty list")
    @SuppressWarnings("unchecked")
    void getCampaigns_emptyForNewClient() {
        String clientId = createTestClient();

        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/clients/" + clientId + "/campaigns", HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEmpty();
    }

    @Test
    @DisplayName("GET campaign by id — returns correct data")
    @SuppressWarnings("unchecked")
    void getCampaignById() {
        String clientId = createTestClient();
        String campId = createCampaign(clientId);

        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/campaigns/" + campId, HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("id")).isEqualTo(campId);
        assertThat(r.getBody().get("clientId")).isEqualTo(clientId);
    }
}
