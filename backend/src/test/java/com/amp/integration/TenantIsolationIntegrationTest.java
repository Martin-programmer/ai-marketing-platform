package com.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRITICAL — verifies that data belonging to agency-001 is invisible to agency-099.
 * Every test creates resources with {@link #agencyAdminHeaders()} (agency 001)
 * and then attempts to read/write them with {@link #otherAgencyHeaders()} (agency 099).
 */
@DisplayName("Tenant Isolation – Security Integration Tests")
class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    private String clientIdAgency001;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Create a client owned by agency-001
        Map<String, Object> body = Map.of(
                "name", "Tenant Client " + UUID.randomUUID(),
                "industry", "FINANCE"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        clientIdAgency001 = r.getBody().get("id").toString();
    }

    // ── Client isolation ────────────────────────────────────────────────

    @Test
    @DisplayName("LIST clients with other agency — does NOT include agency-001's client")
    @SuppressWarnings("unchecked")
    void cannotAccessOtherAgencyClients() {
        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.GET,
                new HttpEntity<>(otherAgencyHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> clients = r.getBody();
        boolean found = clients.stream()
                .anyMatch(c -> clientIdAgency001.equals(c.get("id")));
        assertThat(found).as("Agency-099 must NOT see agency-001's client").isFalse();
    }

    @Test
    @DisplayName("GET client by id with other agency — 404")
    @SuppressWarnings("unchecked")
    void cannotGetOtherAgencyClientById() {
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients/" + clientIdAgency001, HttpMethod.GET,
                new HttpEntity<>(otherAgencyHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Campaign isolation ──────────────────────────────────────────────

    @Test
    @DisplayName("POST campaign for other agency's client — 404 (client not found)")
    @SuppressWarnings("unchecked")
    void cannotCreateCampaignForOtherAgencyClient() {
        Map<String, Object> body = Map.of(
                "clientId", clientIdAgency001,
                "name", "Sneaky Camp",
                "objective", "SALES"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients/" + clientIdAgency001 + "/campaigns", HttpMethod.POST,
                new HttpEntity<>(body, otherAgencyHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("LIST campaigns for other agency's client — empty list, no data leak")
    @SuppressWarnings("unchecked")
    void cannotListOtherAgencyCampaigns() {
        // First create a campaign as agency 001
        Map<String, Object> campBody = Map.of(
                "clientId", clientIdAgency001,
                "name", "Secret Camp " + UUID.randomUUID(),
                "objective", "AWARENESS"
        );
        restTemplate.exchange(
                "/api/v1/clients/" + clientIdAgency001 + "/campaigns", HttpMethod.POST,
                new HttpEntity<>(campBody, agencyAdminHeaders()), Map.class);

        // Try to list with agency 099
        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/clients/" + clientIdAgency001 + "/campaigns", HttpMethod.GET,
                new HttpEntity<>(otherAgencyHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEmpty();
    }

    // ── Suggestion isolation ────────────────────────────────────────────

    @Test
    @DisplayName("LIST suggestions for other agency's client — empty list")
    @SuppressWarnings("unchecked")
    void cannotAccessOtherAgencySuggestions() {
        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/clients/" + clientIdAgency001 + "/suggestions", HttpMethod.GET,
                new HttpEntity<>(otherAgencyHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEmpty();
    }

    // ── Report isolation ────────────────────────────────────────────────

    @Test
    @DisplayName("LIST reports for other agency's client — empty list")
    @SuppressWarnings("unchecked")
    void cannotAccessOtherAgencyReports() {
        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/clients/" + clientIdAgency001 + "/reports", HttpMethod.GET,
                new HttpEntity<>(otherAgencyHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEmpty();
    }

    // ── Meta connection isolation ───────────────────────────────────────

    @Test
    @DisplayName("GET meta connection for other agency's client — null (no data)")
    void cannotAccessOtherAgencyMetaConnection() {
        ResponseEntity<String> r = restTemplate.exchange(
                "/api/v1/clients/" + clientIdAgency001 + "/meta/connection", HttpMethod.GET,
                new HttpEntity<>(otherAgencyHeaders()), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Controller returns null → empty or "null" body
        assertThat(r.getBody()).satisfiesAnyOf(
                body -> assertThat(body).isNull(),
                body -> assertThat(body).isBlank(),
                body -> assertThat(body).isEqualTo("null")
        );
    }
}
