package com.amp.creatives;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreativeAnalysisRepository extends JpaRepository<CreativeAnalysis, UUID> {

    Optional<CreativeAnalysis> findByCreativeAssetId(UUID creativeAssetId);
}
