package com.amp.insights;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InsightDailyRepository extends JpaRepository<InsightDaily, UUID> {

    List<InsightDaily> findAllByAgencyIdAndClientIdAndDateBetween(
            UUID agencyId, UUID clientId, LocalDate from, LocalDate to);

    /**
     * Fetch insights for a specific campaign (entity_type = 'CAMPAIGN' and entity_id = campaignId).
     */
    @Query("SELECT i FROM InsightDaily i " +
           "WHERE i.agencyId = :agencyId " +
           "AND i.entityType = 'CAMPAIGN' AND i.entityId = :campaignId " +
           "AND i.date BETWEEN :from AND :to " +
           "ORDER BY i.date")
    List<InsightDaily> findAllByAgencyIdAndCampaignIdAndDateBetween(
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
           "CASE WHEN SUM(i.spend) > 0 THEN SUM(i.conversions) / SUM(i.spend) ELSE CAST(0 AS java.math.BigDecimal) END) " +
           "FROM InsightDaily i " +
           "WHERE i.agencyId = :agencyId AND i.clientId = :clientId " +
           "AND i.date BETWEEN :from AND :to")
    KpiSummary aggregateKpis(
            @Param("agencyId") UUID agencyId,
            @Param("clientId") UUID clientId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
