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
 * Integration tests for the KPI analytics endpoints:
 * <ul>
 *   <li>GET /clients/{id}/kpis/summary  — period comparison</li>
 *   <li>GET /clients/{id}/kpis/daily    — daily trend data</li>
 *   <li>GET /clients/{id}/kpis/top-campaigns — entity ranking</li>
 * </ul>
 */
@DisplayName("KPI Analytics — Integration Tests")
class KpiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String clientId;

    /** Two campaign UUIDs used across tests. */
    private static final String CAMPAIGN_A = "00000000-0000-0000-0000-00000000ca01";
    private static final String CAMPAIGN_B = "00000000-0000-0000-0000-00000000ca02";
    private static final String ADSET_A    = "00000000-0000-0000-0000-00000000ad01";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Create a client via API (seeds agency via @Sql in BaseIntegrationTest)
        Map<String, Object> body = Map.of(
                "name", "KPI Client " + UUID.randomUUID(),
                "industry", "TECH"
        );
        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/clients", HttpMethod.POST,
                new HttpEntity<>(body, agencyAdminHeaders()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        clientId = r.getBody().get("id").toString();

        // Seed insight_daily rows for the "current" period: 2026-03-01 → 2026-03-03
        seedInsight("2026-03-01", "CAMPAIGN", CAMPAIGN_A, "100.00", 5000, 200, "10.00");
        seedInsight("2026-03-02", "CAMPAIGN", CAMPAIGN_A, "120.00", 6000, 240, "12.00");
        seedInsight("2026-03-03", "CAMPAIGN", CAMPAIGN_A, "110.00", 5500, 210, "11.00");

        // Second campaign with lower spend
        seedInsight("2026-03-01", "CAMPAIGN", CAMPAIGN_B, "40.00", 2000, 80, "4.00");
        seedInsight("2026-03-02", "CAMPAIGN", CAMPAIGN_B, "50.00", 2500, 100, "5.00");

        // An adset row (different entity type)
        seedInsight("2026-03-01", "ADSET", ADSET_A, "20.00", 1000, 40, "2.00");

        // Seed "previous" period: 2026-02-26 → 2026-02-28 (same 3-day length)
        seedInsight("2026-02-26", "CAMPAIGN", CAMPAIGN_A, "80.00", 4000, 160, "8.00");
        seedInsight("2026-02-27", "CAMPAIGN", CAMPAIGN_A, "90.00", 4500, 180, "9.00");
        seedInsight("2026-02-28", "CAMPAIGN", CAMPAIGN_A, "85.00", 4200, 170, "8.50");
    }

    private void seedInsight(String date, String entityType, String entityId,
                             String spend, long impressions, long clicks,
                             String conversions) {
        jdbcTemplate.update("""
                INSERT INTO insight_daily
                    (id, agency_id, client_id, entity_type, entity_id, date,
                     spend, impressions, clicks, ctr, cpc, cpm,
                     conversions, conversion_value, roas, frequency, reach,
                     raw_json, created_at)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?, ?::uuid, ?::date,
                        ?::numeric, ?, ?, 0, 0, 0,
                        ?::numeric, 0, 0, 0, 0,
                        '{}', now())
                ON CONFLICT (entity_type, entity_id, date) DO UPDATE SET
                    spend = EXCLUDED.spend,
                    impressions = EXCLUDED.impressions,
                    clicks = EXCLUDED.clicks,
                    conversions = EXCLUDED.conversions,
                    client_id = EXCLUDED.client_id
                """,
                AGENCY_ID, clientId, entityType, entityId, date,
                spend, impressions, clicks, conversions);
    }

    // ═══════════════════════════════════════════════════════════════
    // KPI Summary with period comparison
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /kpis/summary")
    class KpiSummaryTests {

        @Test
        @DisplayName("returns current, previous, and changes for 3-day period")
        @SuppressWarnings("unchecked")
        void summary_withData() {
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/kpis/summary?from=2026-03-01&to=2026-03-03",
                    HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), Map.class);

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> result = r.getBody();

            // Period boundaries
            Map<String, Object> period = (Map<String, Object>) result.get("period");
            assertThat(period.get("from")).isEqualTo("2026-03-01");
            assertThat(period.get("to")).isEqualTo("2026-03-03");

            Map<String, Object> prevPeriod = (Map<String, Object>) result.get("previousPeriod");
            assertThat(prevPeriod).containsKey("from");

            // Current period has data
            Map<String, Object> current = (Map<String, Object>) result.get("current");
            assertThat(current).containsKeys("totalSpend", "totalImpressions", "totalClicks",
                    "totalConversions", "avgCtr", "avgCpc", "avgRoas");
            // 100 + 120 + 110 + 40 + 50 + 20 = 440 total spend
            assertThat(((Number) current.get("totalSpend")).doubleValue()).isGreaterThan(0);

            // Changes map present
            Map<String, Object> changes = (Map<String, Object>) result.get("changes");
            assertThat(changes).containsKeys("spend", "impressions", "clicks", "conversions");
        }

        @Test
        @DisplayName("returns zeros when no data for period")
        @SuppressWarnings("unchecked")
        void summary_emptyPeriod() {
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/kpis/summary?from=2025-01-01&to=2025-01-07",
                    HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), Map.class);

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> current = (Map<String, Object>) r.getBody().get("current");
            assertThat(((Number) current.get("totalImpressions")).longValue()).isEqualTo(0L);
        }

        @Test
        @DisplayName("AGENCY_USER without CAMPAIGNS_VIEW → 403")
        @SuppressWarnings("unchecked")
        void summary_forbidden() {
            // AGENCY_USER has no permissions granted for this client
            ResponseEntity<Map> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/kpis/summary?from=2026-03-01&to=2026-03-03",
                    HttpMethod.GET,
                    new HttpEntity<>(agencyUserHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // KPI Daily trend
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /kpis/daily")
    class KpiDailyTests {

        @Test
        @DisplayName("returns daily aggregates sorted by date ascending")
        @SuppressWarnings("unchecked")
        void daily_sorted() {
            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/kpis/daily?from=2026-03-01&to=2026-03-03",
                    HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), List.class);

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> days = r.getBody();

            // We seeded data for 3 dates
            assertThat(days).hasSize(3);

            // Sorted ascending
            assertThat(days.get(0).get("date")).isEqualTo("2026-03-01");
            assertThat(days.get(1).get("date")).isEqualTo("2026-03-02");
            assertThat(days.get(2).get("date")).isEqualTo("2026-03-03");

            // First day: CAMPAIGN_A(100) + CAMPAIGN_B(40) + ADSET_A(20) = 160 spend
            assertThat(((Number) days.get(0).get("spend")).doubleValue()).isEqualTo(160.00);
            // First day: 5000 + 2000 + 1000 = 8000 impressions
            assertThat(((Number) days.get(0).get("impressions")).longValue()).isEqualTo(8000L);
            // First day: 200 + 80 + 40 = 320 clicks
            assertThat(((Number) days.get(0).get("clicks")).longValue()).isEqualTo(320L);

            // Each day has ctr and cpc
            assertThat(days.get(0)).containsKeys("ctr", "cpc", "conversions");
        }

        @Test
        @DisplayName("returns empty list when no data")
        @SuppressWarnings("unchecked")
        void daily_empty() {
            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/kpis/daily?from=2025-01-01&to=2025-01-07",
                    HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), List.class);

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).isEmpty();
        }

        @Test
        @DisplayName("last day has only CAMPAIGN_A data (no B/adset)")
        @SuppressWarnings("unchecked")
        void daily_dayThree() {
            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId + "/kpis/daily?from=2026-03-01&to=2026-03-03",
                    HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), List.class);

            List<Map<String, Object>> days = r.getBody();
            // Day 3: only CAMPAIGN_A: spend=110, impressions=5500, clicks=210
            assertThat(((Number) days.get(2).get("spend")).doubleValue()).isEqualTo(110.00);
            assertThat(((Number) days.get(2).get("impressions")).longValue()).isEqualTo(5500L);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Top campaigns (entities by spend)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /kpis/top-campaigns")
    class TopCampaignsTests {

        @Test
        @DisplayName("returns entities sorted by spend descending")
        @SuppressWarnings("unchecked")
        void topCampaigns_sorted() {
            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId
                            + "/kpis/top-campaigns?from=2026-03-01&to=2026-03-03",
                    HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), List.class);

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> top = r.getBody();

            // 3 distinct entities: CAMPAIGN_A, CAMPAIGN_B, ADSET_A
            assertThat(top).hasSize(3);

            // First must be CAMPAIGN_A (highest spend: 100+120+110 = 330)
            assertThat(top.get(0).get("entityType")).isEqualTo("CAMPAIGN");
            assertThat(top.get(0).get("entityId")).isEqualTo(CAMPAIGN_A);
            assertThat(((Number) top.get(0).get("spend")).doubleValue()).isEqualTo(330.00);

            // Second: CAMPAIGN_B (40+50 = 90)
            assertThat(top.get(1).get("entityId")).isEqualTo(CAMPAIGN_B);
            assertThat(((Number) top.get(1).get("spend")).doubleValue()).isEqualTo(90.00);

            // Third: ADSET_A (20)
            assertThat(top.get(2).get("entityId")).isEqualTo(ADSET_A);
            assertThat(((Number) top.get(2).get("spend")).doubleValue()).isEqualTo(20.00);
        }

        @Test
        @DisplayName("limit parameter restricts results")
        @SuppressWarnings("unchecked")
        void topCampaigns_limit() {
            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId
                            + "/kpis/top-campaigns?from=2026-03-01&to=2026-03-03&limit=2",
                    HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), List.class);

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("each entry has all expected fields")
        @SuppressWarnings("unchecked")
        void topCampaigns_fields() {
            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId
                            + "/kpis/top-campaigns?from=2026-03-01&to=2026-03-03",
                    HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), List.class);

            Map<String, Object> first = ((List<Map<String, Object>>) r.getBody()).get(0);
            assertThat(first).containsKeys(
                    "entityType", "entityId", "spend", "impressions",
                    "clicks", "conversions", "ctr", "cpc");
        }

        @Test
        @DisplayName("returns empty list when no data")
        @SuppressWarnings("unchecked")
        void topCampaigns_empty() {
            ResponseEntity<List> r = restTemplate.exchange(
                    "/api/v1/clients/" + clientId
                            + "/kpis/top-campaigns?from=2025-01-01&to=2025-01-07",
                    HttpMethod.GET,
                    new HttpEntity<>(agencyAdminHeaders()), List.class);

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).isEmpty();
        }
    }
}
