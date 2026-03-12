package com.amp.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiAudienceSuggestionRepository extends JpaRepository<AiAudienceSuggestion, UUID> {

    List<AiAudienceSuggestion> findTop3ByAgencyIdAndClientIdOrderByCreatedAtDesc(UUID agencyId, UUID clientId);

    AiAudienceSuggestion findTop1ByAgencyIdAndClientIdOrderByCreatedAtDesc(UUID agencyId, UUID clientId);
}
