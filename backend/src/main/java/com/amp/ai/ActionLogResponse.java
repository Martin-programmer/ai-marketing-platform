package com.amp.ai;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing an AI action log entry.
 */
public record ActionLogResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        UUID suggestionId,
        String executedBy,
        String metaRequestJson,
        String metaResponseJson,
        boolean success,
        String resultSnapshotJson,
        OffsetDateTime createdAt
) {
    public static ActionLogResponse from(AiActionLog e) {
        return new ActionLogResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getSuggestionId(), e.getExecutedBy(),
                e.getMetaRequestJson(), e.getMetaResponseJson(),
                e.isSuccess(), e.getResultSnapshotJson(),
                e.getCreatedAt()
        );
    }
}
