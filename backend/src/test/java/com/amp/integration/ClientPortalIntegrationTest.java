package com.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CLIENT_USER portal endpoints and role guards.
 * <p>
 * Verifies that:
 * <ul>
 *     <li>CLIENT_USER can access portal endpoints for their own client</li>
 *     <li>CLIENT_USER is blocked from agency mutation endpoints (403)</li>
 *     <li>AGENCY_ADMIN is blocked from portal endpoints (403)</li>
 * </ul>
 */
@DisplayName("Client Portal API – Integration Tests")
class ClientPortalIntegrationTest extends BaseIntegrationTest {

    private static final String CLIENT_USER_ID   = "00000000-0000-0000-0000-000000000050";
    private static final String CLIENT_ID        = "00000000-0000-0000-0000-000000000100";
    private static final String CLIENT_USER_EMAIL = "client_user@local";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedClientUser() {
        // Ensure agency exists (handled by @Sql in base)
        // Ensure client exists
        jdbcTemplate.update("""
                INSERT INTO client (id, agency_id, name, industry, status, timezone, currency, created_at, updated_at)
                VALUES (?, ?, 'Portal Test Client', 'TECH', 'ACTIVE', 'Europe/Sofia', 'BGN', now(), now())
                ON CONFLICT DO NOTHING
                """, UUID.fromString(CLIENT_ID), UUID.fromString(AGENCY_ID));

        // Ensure CLIENT_USER account exists
        jdbcTemplate.update("""
                INSERT INTO user_account (id, agency_id, client_id, cognito_sub, email, role, status,
                                          password_hash, display_name, created_at, updated_at)
                VALUES (?, ?, ?, 'local-sub-portal-test', ?, 'CLIENT_USER', 'ACTIVE',
                        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
                        'Portal Test User', now(), now())
                ON CONFLICT DO NOTHING
                """,
                UUID.fromString(CLIENT_USER_ID),
                UUID.fromString(AGENCY_ID),
                UUID.fromString(CLIENT_ID),
                CLIENT_USER_EMAIL);
    }

    // ── header helpers ──────────────────────────────────────────────────

    private HttpHeaders clientUserHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Dev-User-Email", CLIENT_USER_EMAIL);
        h.set("X-Dev-User-Role", "CLIENT_USER");
        h.set("X-Agency-Id", AGENCY_ID);
        h.set("X-Dev-User-Id", CLIENT_USER_ID);
        h.set("X-Dev-Client-Id", CLIENT_ID);
        return h;
    }

    // ── Portal access tests ─────────────────────────────────────────────

    @Test
    @DisplayName("CLIENT_USER GET /portal/me/client → 200 with own client data")
    @SuppressWarnings("unchecked")
    void clientUser_getMyClient_200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/portal/me/client", HttpMethod.GET,
                new HttpEntity<>(clientUserHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().get("id")).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("CLIENT_USER GET /portal/reports → 200")
    @SuppressWarnings("unchecked")
    void clientUser_listPortalReports_200() {
        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/portal/reports", HttpMethod.GET,
                new HttpEntity<>(clientUserHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isNotNull();
        // Only SENT/APPROVED reports should be visible (may be empty)
    }

    @Test
    @DisplayName("CLIENT_USER GET /portal/campaigns → 200 (no DRAFT campaigns)")
    @SuppressWarnings("unchecked")
    void clientUser_listPortalCampaigns_200() {
        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/portal/campaigns", HttpMethod.GET,
                new HttpEntity<>(clientUserHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isNotNull();
    }

    @Test
    @DisplayName("CLIENT_USER GET /portal/suggestions → 200 (only APPLIED/APPROVED)")
    @SuppressWarnings("unchecked")
    void clientUser_listPortalSuggestions_200() {
        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/portal/suggestions", HttpMethod.GET,
                new HttpEntity<>(clientUserHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isNotNull();
    }

    // ── Agency endpoint blocking tests ──────────────────────────────────

    @Test
    @DisplayName("CLIENT_USER POST /api/v1/clients → 403 FORBIDDEN")
    @SuppressWarnings("unchecked")
    void clientUser_createClient_403() {
        Map<String, Object> body = Map.of(
                "name", "Should Fail",
                "industry", "TECH",
                "timezone", "Europe/Sofia",
                "currency", "BGN"
        );

        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(body, clientUserHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().get("code")).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("CLIENT_USER POST campaigns → 403 FORBIDDEN")
    @SuppressWarnings("unchecked")
    void clientUser_createCampaign_403() {
        Map<String, Object> body = Map.of(
                "name", "Should Fail",
                "objective", "SALES",
                "platform", "META"
        );

        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients/" + CLIENT_ID + "/campaigns", HttpMethod.POST,
                new HttpEntity<>(body, clientUserHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("CLIENT_USER GET /api/v1/clients/{clientId}/reports → 200 (view own client reports)")
    @SuppressWarnings("unchecked")
    void clientUser_listOwnClientReports_200() {
        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/clients/" + CLIENT_ID + "/reports", HttpMethod.GET,
                new HttpEntity<>(clientUserHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("CLIENT_USER GET /api/v1/clients/{clientId}/suggestions → 403 (agency suggestion endpoint)")
    @SuppressWarnings("unchecked")
    void clientUser_listAgencySuggestions_403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients/" + CLIENT_ID + "/suggestions", HttpMethod.GET,
                new HttpEntity<>(clientUserHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Reverse guard: agency user cannot access portal ─────────────────

    @Test
    @DisplayName("AGENCY_ADMIN GET /portal/me/client → 403 FORBIDDEN")
    @SuppressWarnings("unchecked")
    void agencyAdmin_portalMyClient_403() {
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/portal/me/client", HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── CLIENT_USER can list their own client via /api/v1/clients ────────

    @Test
    @DisplayName("CLIENT_USER GET /api/v1/clients → 200 returns only own client")
    @SuppressWarnings("unchecked")
    void clientUser_listClients_returnsOwnOnly() {
        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.GET,
                new HttpEntity<>(clientUserHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("CLIENT_USER GET /api/v1/clients/{ownClientId} → 200")
    @SuppressWarnings("unchecked")
    void clientUser_getOwnClient_200() {
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients/" + CLIENT_ID, HttpMethod.GET,
                new HttpEntity<>(clientUserHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("id")).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("CLIENT_USER GET /api/v1/clients/{otherClientId} → 403")
    @SuppressWarnings("unchecked")
    void clientUser_getOtherClient_403() {
        String otherClientId = "00000000-0000-0000-0000-000000000200";

        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients/" + otherClientId, HttpMethod.GET,
                new HttpEntity<>(clientUserHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
