package com.amp.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, UUID> {

    List<AiSuggestion> findAllByAgencyIdAndClientId(UUID agencyId, UUID clientId);

    List<AiSuggestion> findAllByAgencyIdAndClientIdAndStatus(UUID agencyId, UUID clientId, String status);

    Optional<AiSuggestion> findByIdAndAgencyId(UUID id, UUID agencyId);

    /** Guardrails: cooldown & deduplication check. */
    List<AiSuggestion> findByAgencyIdAndClientIdAndScopeTypeAndScopeIdAndSuggestionTypeAndCreatedAtAfter(
            UUID agencyId, UUID clientId, String scopeType, UUID scopeId,
            String suggestionType, OffsetDateTime after);

    /** Guardrails: cumulative budget change check. */
    List<AiSuggestion> findByAgencyIdAndClientIdAndScopeIdAndSuggestionTypeAndStatusInAndCreatedAtAfter(
            UUID agencyId, UUID clientId, UUID scopeId, String suggestionType,
            List<String> statuses, OffsetDateTime after);
}
