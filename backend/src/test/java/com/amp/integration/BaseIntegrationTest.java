package com.amp.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Abstract base class for integration tests using Testcontainers with PostgreSQL.
 * <p>
 * Uses the <b>singleton container pattern</b>: a single PostgreSQL container is
 * started once and shared across all test classes for speed.
 * <p>
 * Profiles: "local" activates DevAuthFilter + SecurityConfig,
 * "test" loads application-test.yml overrides.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "test"})
@Sql(statements = "INSERT INTO agency (id, name, status, plan_code) VALUES " +
        "('00000000-0000-0000-0000-000000000001', 'Test Agency 001', 'ACTIVE', 'PRO') " +
        "ON CONFLICT DO NOTHING",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class BaseIntegrationTest {

    protected static final String AGENCY_ID     = "00000000-0000-0000-0000-000000000001";
    protected static final String OTHER_AGENCY   = "00000000-0000-0000-0000-000000000099";
    protected static final String USER_ID        = "00000000-0000-0000-0000-000000000010";
    protected static final String OTHER_USER_ID  = "00000000-0000-0000-0000-000000000099";

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("amp_test")
                .withUsername("amp_test")
                .withPassword("amp_test");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username",  postgres::getUsername);
        registry.add("spring.datasource.password",  postgres::getPassword);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    // ── header helpers ──────────────────────────────────────────────────

    /**
     * Headers that simulate an AGENCY_ADMIN belonging to agency 001.
     */
    protected HttpHeaders agencyAdminHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Dev-User-Email", "admin@agency001.com");
        h.set("X-Dev-User-Role", "AGENCY_ADMIN");
        h.set("X-Agency-Id", AGENCY_ID);
        h.set("X-Dev-User-Id", USER_ID);
        return h;
    }

    /**
     * Headers that simulate a user belonging to a DIFFERENT agency (099).
     */
    protected HttpHeaders otherAgencyHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Dev-User-Email", "admin@agency099.com");
        h.set("X-Dev-User-Role", "AGENCY_ADMIN");
        h.set("X-Agency-Id", OTHER_AGENCY);
        h.set("X-Dev-User-Id", OTHER_USER_ID);
        return h;
    }
}
