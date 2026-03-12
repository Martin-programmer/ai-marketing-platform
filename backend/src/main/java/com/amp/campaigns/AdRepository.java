package com.amp.campaigns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdRepository extends JpaRepository<Ad, UUID> {

    List<Ad> findAllByAdsetId(UUID adsetId);

    List<Ad> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    Optional<Ad> findByIdAndAgencyId(UUID id, UUID agencyId);

    Optional<Ad> findByAgencyIdAndClientIdAndMetaAdId(UUID agencyId, UUID clientId, String metaAdId);
}
