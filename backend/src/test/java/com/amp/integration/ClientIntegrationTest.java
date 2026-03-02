package com.amp.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Client API – Integration Tests")
class ClientIntegrationTest extends BaseIntegrationTest {

    // ── helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> clientBody(String name) {
        return Map.of(
                "name", name,
                "industry", "TECH",
                "timezone", "Europe/Sofia",
                "currency", "BGN"
        );
    }

    @SuppressWarnings("unchecked")
    private String createClient(String name) {
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(clientBody(name), agencyAdminHeaders()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().get("id").toString();
    }

    // ── tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST + GET — create client, then fetch by id")
    @SuppressWarnings("unchecked")
    void createAndGetClient() {
        String name = "IT Client " + UUID.randomUUID();

        // CREATE
        ResponseEntity<Map> create = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(clientBody(name), agencyAdminHeaders()), Map.class);

        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = create.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("status")).isEqualTo("ACTIVE");
        assertThat(body.get("agencyId")).isEqualTo(AGENCY_ID);

        // GET
        String clientId = body.get("id").toString();
        ResponseEntity<Map> get = restTemplate.exchange(
                "/api/v1/clients/" + clientId, HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);

        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get.getBody().get("name")).isEqualTo(name);
    }

    @Test
    @DisplayName("GET list — returns at least the client we just created")
    @SuppressWarnings("unchecked")
    void listClients() {
        String name = "List Test " + UUID.randomUUID();
        createClient(name);

        ResponseEntity<List> response = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("PAUSE + ACTIVATE — lifecycle transitions")
    @SuppressWarnings("unchecked")
    void pauseAndActivateClient() {
        String clientId = createClient("Lifecycle " + UUID.randomUUID());

        // PAUSE
        ResponseEntity<Map> pause = restTemplate.exchange(
                "/api/v1/clients/" + clientId + "/pause", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);
        assertThat(pause.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pause.getBody().get("status")).isEqualTo("PAUSED");

        // ACTIVATE
        ResponseEntity<Map> activate = restTemplate.exchange(
                "/api/v1/clients/" + clientId + "/activate", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);
        assertThat(activate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activate.getBody().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("POST with blank name — 400 validation error")
    @SuppressWarnings("unchecked")
    void createClient_invalidData() {
        Map<String, Object> body = Map.of("name", "", "industry", "TECH");

        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("POST duplicate name — 409 conflict")
    @SuppressWarnings("unchecked")
    void createClient_duplicateName() {
        String name = "Dup " + UUID.randomUUID();
        createClient(name);

        // second create with same name
        ResponseEntity<Map> dup = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(clientBody(name), agencyAdminHeaders()), Map.class);

        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("GET non-existent client — 404")
    @SuppressWarnings("unchecked")
    void getClient_notFound() {
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
