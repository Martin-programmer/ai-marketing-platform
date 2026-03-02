package com.amp.meta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetaSyncJobRepository extends JpaRepository<MetaSyncJob, UUID> {

    List<MetaSyncJob> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    Optional<MetaSyncJob> findByIdAndAgencyId(UUID id, UUID agencyId);

    Optional<MetaSyncJob> findFirstByClientIdOrderByRequestedAtDesc(UUID clientId);
}
