package com.amp.campaigns;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    Optional<Campaign> findByIdAndAgencyId(UUID id, UUID agencyId);

    Optional<Campaign> findByAgencyIdAndClientIdAndMetaCampaignId(UUID agencyId, UUID clientId, String metaCampaignId);

    List<Campaign> findByAgencyIdAndClientIdIn(UUID agencyId, List<UUID> clientIds);

    @Query(value = """
            SELECT
                c.id AS campaignId,
                c.name AS campaignName,
                c.status AS status,
                COALESCE(SUM(i.spend), 0) AS spend,
                COALESCE(SUM(i.impressions), 0) AS impressions,
                COALESCE(SUM(i.clicks), 0) AS clicks,
                COALESCE(SUM(i.conversions), 0) AS conversions,
                COALESCE(SUM(i.conversion_value), 0) AS conversionValue,
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
                    WHEN COALESCE(SUM(i.spend), 0) > 0
                        THEN COALESCE(SUM(i.conversion_value), 0) / COALESCE(SUM(i.spend), 0)
                    ELSE 0
                END AS roas
            FROM campaign c
            LEFT JOIN adset ast ON ast.campaign_id = c.id
            LEFT JOIN ad a ON a.adset_id = ast.id
            LEFT JOIN insight_daily i
                ON i.entity_type = 'AD'
               AND i.entity_id = a.id
               AND i.agency_id = :agencyId
               AND i.client_id = :clientId
               AND i.date BETWEEN :from AND :to
            WHERE c.agency_id = :agencyId
              AND c.client_id = :clientId
            GROUP BY c.id, c.name, c.status
            ORDER BY COALESCE(SUM(i.spend), 0) DESC, c.name ASC
            """, nativeQuery = true)
    List<CampaignPerformanceProjection> findCampaignPerformance(
            @Param("agencyId") UUID agencyId,
            @Param("clientId") UUID clientId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
