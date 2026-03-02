package com.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Report API – Integration Tests")
class ReportIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String clientId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Map<String, Object> body = Map.of(
                "name", "Report Client " + UUID.randomUUID(),
                "industry", "MEDIA"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        clientId = r.getBody().get("id").toString();
    }

    // ── tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Generate report, then GET — DRAFT status persisted")
    @SuppressWarnings("unchecked")
    void generateAndGetReport() {
        Map<String, Object> body = Map.of(
                "clientId", clientId,
                "reportType", "WEEKLY",
                "periodStart", LocalDate.now().minusDays(7).toString(),
                "periodEnd", LocalDate.now().toString()
        );

        // GENERATE
        ResponseEntity<Map> gen = restTemplate.exchange(
                "/api/v1/clients/" + clientId + "/reports/generate", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);

        assertThat(gen.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> report = gen.getBody();
        assertThat(report.get("status")).isEqualTo("DRAFT");
        String reportId = report.get("id").toString();

        // LIST
        ResponseEntity<List> list = restTemplate.exchange(
                "/api/v1/clients/" + clientId + "/reports", HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), List.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().size()).isGreaterThanOrEqualTo(1);

        // GET by id
        ResponseEntity<Map> get = restTemplate.exchange(
                "/api/v1/reports/" + reportId, HttpMethod.GET,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);

        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get.getBody().get("id")).isEqualTo(reportId);
        assertThat(get.getBody().get("reportType")).isEqualTo("WEEKLY");
    }

    @Test
    @DisplayName("Send DRAFT report fails (409), update to APPROVED then send succeeds")
    @SuppressWarnings("unchecked")
    void sendReport_mustBeApproved() {
        // GENERATE a DRAFT report
        Map<String, Object> body = Map.of(
                "clientId", clientId,
                "reportType", "MONTHLY",
                "periodStart", LocalDate.now().minusMonths(1).toString(),
                "periodEnd", LocalDate.now().toString()
        );
        ResponseEntity<Map> gen = restTemplate.exchange(
                "/api/v1/clients/" + clientId + "/reports/generate", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
        assertThat(gen.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String reportId = gen.getBody().get("id").toString();

        // TRY SEND while DRAFT — should fail 409
        ResponseEntity<Map> sendDraft = restTemplate.exchange(
                "/api/v1/reports/" + reportId + "/send", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);

        assertThat(sendDraft.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Manually set status to APPROVED via JDBC
        jdbcTemplate.update("UPDATE report SET status = 'APPROVED' WHERE id = ?::uuid", reportId);

        // SEND again — should succeed
        ResponseEntity<Map> sendApproved = restTemplate.exchange(
                "/api/v1/reports/" + reportId + "/send", HttpMethod.POST,
                new HttpEntity<>(agencyAdminHeaders()), Map.class);

        assertThat(sendApproved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sendApproved.getBody().get("status")).isEqualTo("SENT");
    }
}
