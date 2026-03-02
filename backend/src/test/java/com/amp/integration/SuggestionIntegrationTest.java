package com.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AI Suggestion API – Integration Tests")
class SuggestionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String clientId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Map<String, Object> body = Map.of(
                "name", "Suggest Client " + UUID.randomUUID(),
                "industry", "E_COMMERCE"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        clientId = r.getBody().get("id").toString();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private UUID insertSuggestion(String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ai_suggestion " +
                        "(id, agency_id, client_id, scope_type, scope_id, " +
                        " suggestion_type, payload_json, rationale, confidence, " +
                        " risk_level, status, created_by, created_at) " +
                        "VALUES (?, ?::uuid, ?::uuid, ?, ?::uuid, ?, ?::jsonb, ?, ?, ?, ?, ?, now())",
                id,
                AGENCY_ID,
                clientId,
                "CAMPAIGN",
                UUID.randomUUID(),
                "BUDGET_ADJUST",
                "{\"recommended_budget\": 150}",
                "Test rationale for integration test",
                new BigDecimal("0.850"),
                "MEDIUM",
                status,
                "AI"
        );
        return id;
    }

    // ── tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Lifecycle: PENDING → APPROVED → APPLIED")
    @SuppressWarnings("unchecked")
    void suggestionLifecycle() {
        UUID sugId = insertSuggestion("PENDING");

        // GET
        ResponseEntity<Map> get = restTemplate.exchange(
                "/api/v1/suggestions/" + sugId, HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get.getBody().get("status")).isEqualTo("PENDING");

        // APPROVE
        ResponseEntity<Map> approve = restTemplate.exchange(
                "/api/v1/suggestions/" + sugId + "/approve", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);
        assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approve.getBody().get("status")).isEqualTo("APPROVED");

        // APPLY
        ResponseEntity<Map> apply = restTemplate.exchange(
                "/api/v1/suggestions/" + sugId + "/apply", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);
        assertThat(apply.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apply.getBody().get("status")).isEqualTo("APPLIED");
    }

    @Test
    @DisplayName("REJECT — then approve fails")
    @SuppressWarnings("unchecked")
    void rejectSuggestion() {
        UUID sugId = insertSuggestion("PENDING");

        // REJECT
        ResponseEntity<Map> reject = restTemplate.exchange(
                "/api/v1/suggestions/" + sugId + "/reject", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);
        assertThat(reject.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reject.getBody().get("status")).isEqualTo("REJECTED");

        // try APPROVE after reject — should fail (409 CONFLICT)
        ResponseEntity<Map> approve = restTemplate.exchange(
                "/api/v1/suggestions/" + sugId + "/approve", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);
        assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("GET filtered by status — only PENDING returned")
    @SuppressWarnings("unchecked")
    void getSuggestions_filterByStatus() {
        insertSuggestion("PENDING");
        insertSuggestion("APPROVED");
        insertSuggestion("REJECTED");

        ResponseEntity<List> r = restTemplate.exchange(
                "/api/v1/clients/" + clientId + "/suggestions?status=PENDING",
                HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> suggestions = r.getBody();
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions).allSatisfy(s ->
                assertThat(s.get("status")).isEqualTo("PENDING")
        );
    }
}
