package com.amp.ai;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing an AI suggestion.
 */
public record SuggestionResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String scopeType,
        UUID scopeId,
        String suggestionType,
        String payloadJson,
        String rationale,
        BigDecimal confidence,
        String riskLevel,
        String status,
        OffsetDateTime cooldownUntil,
        String createdBy,
        OffsetDateTime createdAt,
        UUID reviewedBy,
        OffsetDateTime reviewedAt
) {
    public static SuggestionResponse from(AiSuggestion e) {
        return new SuggestionResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getScopeType(), e.getScopeId(),
                e.getSuggestionType(), e.getPayloadJson(),
                e.getRationale(), e.getConfidence(), e.getRiskLevel(),
                e.getStatus(), e.getCooldownUntil(),
                e.getCreatedBy(), e.getCreatedAt(),
                e.getReviewedBy(), e.getReviewedAt()
        );
    }
}
