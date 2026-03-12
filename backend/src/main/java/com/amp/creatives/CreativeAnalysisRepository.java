package com.amp.creatives;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreativeAnalysisRepository extends JpaRepository<CreativeAnalysis, UUID> {

    Optional<CreativeAnalysis> findByCreativeAssetId(UUID creativeAssetId);

    java.util.List<CreativeAnalysis> findAllByAgencyIdAndClientIdOrderByQualityScoreDescCreatedAtDesc(UUID agencyId, UUID clientId);

    java.util.List<CreativeAnalysis> findTop5ByAgencyIdAndClientIdOrderByCreatedAtDesc(UUID agencyId, UUID clientId);
}
