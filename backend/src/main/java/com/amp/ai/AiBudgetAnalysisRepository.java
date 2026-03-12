package com.amp.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiBudgetAnalysisRepository extends JpaRepository<AiBudgetAnalysis, UUID> {

    AiBudgetAnalysis findTop1ByAgencyIdAndClientIdOrderByCreatedAtDesc(UUID agencyId, UUID clientId);
}
