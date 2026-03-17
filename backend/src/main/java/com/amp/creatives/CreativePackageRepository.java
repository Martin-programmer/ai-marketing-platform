package com.amp.creatives;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreativePackageRepository extends JpaRepository<CreativePackage, UUID> {

    List<CreativePackage> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    List<CreativePackage> findAllByAgencyIdAndClientIdAndStatusOrderByApprovedAtDesc(UUID agencyId, UUID clientId, String status);

    Optional<CreativePackage> findByIdAndAgencyId(UUID id, UUID agencyId);
}
