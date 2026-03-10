package com.amp.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiWeeklyDigestRepository extends JpaRepository<AiWeeklyDigest, UUID> {

    List<AiWeeklyDigest> findAllByAgencyIdAndClientIdOrderByCreatedAtDesc(UUID agencyId, UUID clientId);

    Optional<AiWeeklyDigest> findByIdAndAgencyId(UUID id, UUID agencyId);
}
