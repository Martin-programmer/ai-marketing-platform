package com.amp.insights;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsightDailyRepository extends JpaRepository<InsightDaily, UUID> {

    Optional<InsightDaily> findByAgencyIdAndClientIdAndEntityTypeAndEntityIdAndDate(
            UUID agencyId, UUID clientId, String entityType, UUID entityId, LocalDate date);

    List<InsightDaily> findAllByAgencyIdAndClientIdAndDateBetween(
            UUID agencyId, UUID clientId, LocalDate from, LocalDate to);

    List<InsightDaily> findAllByAgencyIdAndClientIdAndEntityTypeAndEntityIdInAndDateBetween(
            UUID agencyId, UUID clientId, String entityType, List<UUID> entityIds, LocalDate from, LocalDate to);

        /**
         * Fetch campaign-level daily insights by rolling up AD-level insight rows.
         */
        @Query(value = """
                        SELECT
                                c.id AS entityId,
                                c.client_id AS clientId,
                                i.date AS date,
                                COALESCE(SUM(i.spend), 0) AS spend,
                                COALESCE(SUM(i.impressions), 0) AS impressions,
                                COALESCE(SUM(i.clicks), 0) AS clicks,
                                CASE
                                        WHEN COALESCE(SUM(i.impressions), 0) > 0
                                                THEN (COALESCE(SUM(i.clicks), 0)::numeric / COALESCE(SUM(i.impressions), 0)) * 100
                                        ELSE 0
                                END AS ctr,
                                CASE
                                        WHEN COALESCE(SUM(i.clicks), 0) > 0
                                                THEN COALESCE(SUM(i.spend), 0) / COALESCE(SUM(i.clicks), 0)
                                        ELSE 0
                                END AS cpc,
                                CASE
                                        WHEN COALESCE(SUM(i.impressions), 0) > 0
                                                THEN (COALESCE(SUM(i.spend), 0) * 1000) / COALESCE(SUM(i.impressions), 0)
                                        ELSE 0
                                END AS cpm,
                                COALESCE(SUM(i.conversions), 0) AS conversions,
                                COALESCE(SUM(i.conversion_value), 0) AS conversionValue,
                                CASE
                                        WHEN COALESCE(SUM(i.spend), 0) > 0
                                                THEN COALESCE(SUM(i.conversion_value), 0) / COALESCE(SUM(i.spend), 0)
                                        ELSE 0
                                END AS roas,
                                CASE
                                        WHEN COALESCE(SUM(i.reach), 0) > 0
                                                THEN COALESCE(SUM(i.impressions), 0)::numeric / COALESCE(SUM(i.reach), 0)
                                        ELSE 0
                                END AS frequency,
                                COALESCE(SUM(i.reach), 0) AS reach
                        FROM campaign c
                        JOIN adset ast ON ast.campaign_id = c.id
                        JOIN ad a ON a.adset_id = ast.id
                        JOIN insight_daily i ON i.entity_type = 'AD' AND i.entity_id = a.id
                        WHERE c.agency_id = :agencyId
                          AND c.id = :campaignId
                          AND i.date BETWEEN :from AND :to
                        GROUP BY c.id, c.client_id, i.date
                        ORDER BY i.date
                        """, nativeQuery = true)
        List<CampaignDailyInsightProjection> findAllByAgencyIdAndCampaignIdAndDateBetween(
            @Param("agencyId") UUID agencyId,
            @Param("campaignId") UUID campaignId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Aggregated KPI summary for a client within a date range.
     */
    @Query("SELECT new com.amp.insights.KpiSummary(" +
           "SUM(i.impressions), SUM(i.clicks), SUM(i.spend), " +
           "SUM(i.conversions), " +
           "CASE WHEN SUM(i.impressions) > 0 THEN CAST(SUM(i.clicks) AS double) / SUM(i.impressions) * 100 ELSE 0.0 END, " +
           "CASE WHEN SUM(i.clicks) > 0 THEN SUM(i.spend) / SUM(i.clicks) ELSE CAST(0 AS java.math.BigDecimal) END, " +
           "CASE WHEN SUM(i.spend) > 0 THEN SUM(i.conversionValue) / SUM(i.spend) ELSE CAST(0 AS java.math.BigDecimal) END) " +
           "FROM InsightDaily i " +
           "WHERE i.agencyId = :agencyId AND i.clientId = :clientId " +
           "AND i.date BETWEEN :from AND :to")
    KpiSummary aggregateKpis(
            @Param("agencyId") UUID agencyId,
            @Param("clientId") UUID clientId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
