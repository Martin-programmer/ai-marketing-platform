package com.amp.campaigns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdsetRepository extends JpaRepository<Adset, UUID> {

    List<Adset> findAllByCampaignId(UUID campaignId);

    @Query("SELECT a.id FROM Adset a WHERE a.campaignId = :campaignId")
    List<UUID> findIdsByCampaignId(@Param("campaignId") UUID campaignId);

    Optional<Adset> findByIdAndAgencyId(UUID id, UUID agencyId);

    Optional<Adset> findByAgencyIdAndClientIdAndMetaAdsetId(UUID agencyId, UUID clientId, String metaAdsetId);
}
