package com.amp.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a feedback entry.
 */
public record FeedbackResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String sourceType,
        UUID sourceId,
        int rating,
        String comment,
        UUID createdBy,
        OffsetDateTime createdAt
) {
    public static FeedbackResponse from(Feedback e) {
        return new FeedbackResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getSourceType(), e.getSourceId(),
                e.getRating(), e.getComment(),
                e.getCreatedBy(), e.getCreatedAt()
        );
    }
}
