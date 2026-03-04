package com.amp.campaigns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    Optional<Campaign> findByIdAndAgencyId(UUID id, UUID agencyId);

    Optional<Campaign> findByAgencyIdAndClientIdAndMetaCampaignId(UUID agencyId, UUID clientId, String metaCampaignId);

    List<Campaign> findByAgencyIdAndClientIdIn(UUID agencyId, List<UUID> clientIds);
}
