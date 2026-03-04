package com.amp.campaigns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdsetRepository extends JpaRepository<Adset, UUID> {

    List<Adset> findAllByCampaignId(UUID campaignId);

    Optional<Adset> findByIdAndAgencyId(UUID id, UUID agencyId);

    Optional<Adset> findByAgencyIdAndClientIdAndMetaAdsetId(UUID agencyId, UUID clientId, String metaAdsetId);
}
