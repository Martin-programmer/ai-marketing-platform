package com.amp.creatives;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CopyVariantRepository extends JpaRepository<CopyVariant, UUID> {

    List<CopyVariant> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    List<CopyVariant> findByCreativeAssetId(UUID creativeAssetId);
}
