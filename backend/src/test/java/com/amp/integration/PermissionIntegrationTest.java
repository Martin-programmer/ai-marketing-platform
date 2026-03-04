package com.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the permission system end-to-end.
 * <p>
 * Verifies:
 * <ul>
 *   <li>OWNER_ADMIN can access any agency's clients and endpoints</li>
 *   <li>AGENCY_USER with granted permissions can view/edit accordingly</li>
 *   <li>AGENCY_USER without permissions is blocked (403)</li>
 *   <li>Permission management endpoints enforce AGENCY_ADMIN guard</li>
 *   <li>OWNER_ADMIN can manage agencies via /owner/* endpoints</li>
 * </ul>
 */
@DisplayName("Permission System – Integration Tests")
class PermissionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String clientId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Ensure AGENCY_ADMIN user exists (used in agencyAdminHeaders)
        jdbcTemplate.update("""
                INSERT INTO user_account (id, agency_id, cognito_sub, email, role, status,
                                          password_hash, display_name, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, 'local-sub-agency-admin', 'admin@agency001.com',
                        'AGENCY_ADMIN', 'ACTIVE',
                        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
                        'Agency Admin', now(), now())
                ON CONFLICT DO NOTHING
                """, USER_ID, AGENCY_ID);

        // Ensure AGENCY_USER account exists
        jdbcTemplate.update("""
                INSERT INTO user_account (id, agency_id, cognito_sub, email, role, status,
                                          password_hash, display_name, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, 'local-sub-agency-user', 'agency_user@local',
                        'AGENCY_USER', 'ACTIVE',
                        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
                        'Agency User', now(), now())
                ON CONFLICT DO NOTHING
                """, AGENCY_USER_ID, AGENCY_ID);

        // Ensure OWNER_ADMIN account exists (null agency_id)
        jdbcTemplate.update("""
                INSERT INTO user_account (id, agency_id, cognito_sub, email, role, status,
                                          password_hash, display_name, created_at, updated_at)
                VALUES (?::uuid, NULL, 'local-sub-owner-admin', 'owner_admin@local',
                        'OWNER_ADMIN', 'ACTIVE',
                        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
                        'Owner Admin', now(), now())
                ON CONFLICT DO NOTHING
                """, OWNER_USER_ID);

        // Create a client owned by agency-001
        Map<String, Object> body = Map.of(
                "name", "Perm Client " + UUID.randomUUID(),
                "industry", "TECH"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        clientId = r.getBody().get("id").toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // OWNER_ADMIN — can see everything
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OWNER_ADMIN access")
    class OwnerAdminAccess {

        @Test
        @DisplayName("GET /owner/dashboard → 200")
        @SuppressWarnings("unchecked")
        void ownerDashboard_200() {
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/owner/dashboard", HttpMethod.GET,
                    new HttpEntity<>(ownerAdminHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).containsKeys("totalAgencies", "totalUsers");
        }

        @Test
        @DisplayName("GET /owner/agencies → 200 list of agencies")
        @SuppressWarnings("unchecked")
        void ownerListAgencies_200() {
            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/owner/agencies", HttpMethod.GET,
                    new HttpEntity<>(ownerAdminHeaders()), List.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).isNotEmpty();
        }

        @Test
        @DisplayName("AGENCY_ADMIN cannot access /owner/dashboard → 403")
        @SuppressWarnings("unchecked")
        void agencyAdmin_ownerDashboard_403() {
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/owner/dashboard", HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("AGENCY_USER cannot access /owner/agencies → 403")
        @SuppressWarnings("unchecked")
        void agencyUser_ownerAgencies_403() {
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/owner/agencies", HttpMethod.GET,
                    new HttpEntity<>(agencyUserHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AGENCY_USER — permission-gated access
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AGENCY_USER permission-gated access")
    class AgencyUserPermissions {

        private void grantPermissions(String... permissions) {
            for (String perm : permissions) {
                jdbcTemplate.update("""
                        INSERT INTO user_client_permission (id, user_id, client_id, permission, created_at)
                        VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?, now())
                        ON CONFLICT (user_id, client_id, permission) DO NOTHING
                        """, AGENCY_USER_ID, clientId, perm);
            }
        }

        private void clearPermissions() {
            jdbcTemplate.update(
                    "DELETE FROM user_client_permission WHERE user_id = ?::uuid",
                    AGENCY_USER_ID);
        }

        @Test
        @DisplayName("AGENCY_USER with CAMPAIGNS_VIEW can list campaigns")
        @SuppressWarnings("unchecked")
        void withCampaignsView_canListCampaigns() {
            grantPermissions("CAMPAIGNS_VIEW");
            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/campaigns", HttpMethod.GET,
                    new HttpEntity<>(agencyUserHeaders()), List.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("AGENCY_USER without CAMPAIGNS_VIEW cannot list campaigns → 403")
        @SuppressWarnings("unchecked")
        void withoutCampaignsView_cannotListCampaigns() {
            clearPermissions();
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/campaigns", HttpMethod.GET,
                    new HttpEntity<>(agencyUserHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("AGENCY_USER with CAMPAIGNS_VIEW but not CAMPAIGNS_EDIT cannot create campaign → 403")
        @SuppressWarnings("unchecked")
        void withViewOnly_cannotCreateCampaign() {
            clearPermissions();
            grantPermissions("CAMPAIGNS_VIEW", "CLIENT_VIEW");

            Map<String, Object> body = Map.of(
                    "clientId", clientId,
                    "name", "Forbidden Campaign",
                    "objective", "SALES"
            );
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/campaigns", HttpMethod.POST,
                    new HttpEntity<>(body, agencyUserHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("AGENCY_USER with CLIENT_VIEW can get client")
        @SuppressWarnings("unchecked")
        void withClientView_canGetClient() {
            grantPermissions("CLIENT_VIEW");
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId, HttpMethod.GET,
                    new HttpEntity<>(agencyUserHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody().get("id")).isEqualTo(clientId);
        }

        @Test
        @DisplayName("AGENCY_USER without CLIENT_VIEW cannot get client → 403")
        @SuppressWarnings("unchecked")
        void withoutClientView_cannotGetClient() {
            clearPermissions();
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId, HttpMethod.GET,
                    new HttpEntity<>(agencyUserHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("AGENCY_USER with REPORTS_VIEW can list reports")
        @SuppressWarnings("unchecked")
        void withReportsView_canListReports() {
            grantPermissions("REPORTS_VIEW");
            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/reports", HttpMethod.GET,
                    new HttpEntity<>(agencyUserHeaders()), List.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("AGENCY_USER without REPORTS_VIEW cannot list reports → 403")
        @SuppressWarnings("unchecked")
        void withoutReportsView_cannotListReports() {
            clearPermissions();
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/reports", HttpMethod.GET,
                    new HttpEntity<>(agencyUserHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("AGENCY_USER sees only assigned clients in list")
        @SuppressWarnings("unchecked")
        void listClients_onlyAssigned() {
            clearPermissions();
            grantPermissions("CLIENT_VIEW");

            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients", HttpMethod.GET,
                    new HttpEntity<>(agencyUserHeaders()), List.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> clients = r.getBody();
            assertThat(clients).hasSize(1);
            assertThat(clients.get(0).get("id")).isEqualTo(clientId);
        }

        @Test
        @DisplayName("AGENCY_USER with no assignments sees empty client list")
        @SuppressWarnings("unchecked")
        void listClients_noAssignments() {
            clearPermissions();

            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients", HttpMethod.GET,
                    new HttpEntity<>(agencyUserHeaders()), List.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Permission management endpoints (PermissionController)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Permission management endpoints")
    class PermissionManagement {

        @Test
        @DisplayName("GET /permissions/available → 200 returns all permissions and presets")
        @SuppressWarnings("unchecked")
        void availablePermissions_200() {
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/permissions/available", HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).containsKeys("permissions", "presets");
            List<Map<String, Object>> perms = (List<Map<String, Object>>) r.getBody().get("permissions");
            assertThat(perms).hasSize(13);
        }

        @Test
        @DisplayName("PUT /permissions/users/{userId}/clients/{clientId} — set permissions")
        @SuppressWarnings("unchecked")
        void setPermissions_200() {
            Map<String, Object> body = Map.of(
                    "permissions", List.of("CLIENT_VIEW", "CAMPAIGNS_VIEW", "REPORTS_VIEW")
            );
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/permissions/users/" + AGENCY_USER_ID + "/clients/" + clientId,
                    HttpMethod.PUT,
                    new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<String> perms = (List<String>) r.getBody().get("permissions");
            assertThat(perms).containsExactlyInAnyOrder("CLIENT_VIEW", "CAMPAIGNS_VIEW", "REPORTS_VIEW");
        }

        @Test
        @DisplayName("POST preset EDITOR — applies correct permissions")
        @SuppressWarnings("unchecked")
        void applyPreset_editor() {
            Map<String, Object> body = Map.of("preset", "EDITOR");
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/permissions/users/" + AGENCY_USER_ID + "/clients/" + clientId + "/preset",
                    HttpMethod.POST,
                    new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<String> perms = (List<String>) r.getBody().get("permissions");
            assertThat(perms).hasSize(10);
            assertThat(perms).contains("CLIENT_VIEW", "CLIENT_EDIT", "CAMPAIGNS_VIEW", "CAMPAIGNS_EDIT");
            assertThat(perms).doesNotContain("CAMPAIGNS_PUBLISH", "REPORTS_SEND", "META_MANAGE");
        }

        @Test
        @DisplayName("AGENCY_USER cannot set permissions → 403")
        @SuppressWarnings("unchecked")
        void agencyUser_cannotSetPermissions() {
            Map<String, Object> body = Map.of(
                    "permissions", List.of("CLIENT_VIEW")
            );
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/permissions/users/" + AGENCY_USER_ID + "/clients/" + clientId,
                    HttpMethod.PUT,
                    new HttpEntity<>(body, agencyUserHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("DELETE /permissions/users/{userId}/clients/{clientId} — removes permissions")
        @SuppressWarnings("unchecked")
        void removePermissions_200() {
            // First set some permissions
            jdbcTemplate.update("""
                    INSERT INTO user_client_permission (id, user_id, client_id, permission, created_at)
                    VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'CLIENT_VIEW', now())
                    ON CONFLICT (user_id, client_id, permission) DO NOTHING
                    """, AGENCY_USER_ID, clientId);

            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/permissions/users/" + AGENCY_USER_ID + "/clients/" + clientId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(agencyAdminHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
