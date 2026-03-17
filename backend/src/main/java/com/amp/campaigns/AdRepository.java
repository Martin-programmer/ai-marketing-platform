package com.amp.campaigns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdRepository extends JpaRepository<Ad, UUID> {

    List<Ad> findAllByAdsetId(UUID adsetId);

    @Query("SELECT a.id FROM Ad a WHERE a.adsetId IN :adsetIds")
    List<UUID> findIdsByAdsetIdIn(@Param("adsetIds") List<UUID> adsetIds);

    List<Ad> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    Optional<Ad> findByIdAndAgencyId(UUID id, UUID agencyId);

    Optional<Ad> findByAgencyIdAndClientIdAndMetaAdId(UUID agencyId, UUID clientId, String metaAdId);
}
