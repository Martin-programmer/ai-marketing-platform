package com.amp.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiActionLogRepository extends JpaRepository<AiActionLog, UUID> {

    List<AiActionLog> findAllBySuggestionId(UUID suggestionId);

    List<AiActionLog> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    List<AiActionLog> findTop5ByAgencyIdAndClientIdOrderByCreatedAtDesc(UUID agencyId, UUID clientId);
}
