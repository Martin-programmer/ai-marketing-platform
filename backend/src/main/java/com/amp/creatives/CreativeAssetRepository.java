package com.amp.creatives;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreativeAssetRepository extends JpaRepository<CreativeAsset, UUID> {

    List<CreativeAsset> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    Optional<CreativeAsset> findByIdAndAgencyId(UUID id, UUID agencyId);
}
