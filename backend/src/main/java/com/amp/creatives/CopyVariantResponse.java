package com.amp.creatives;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a copy variant.
 */
public record CopyVariantResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String language,
        String primaryText,
        String headline,
        String description,
        String cta,
        String status,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CopyVariantResponse from(CopyVariant e) {
        return new CopyVariantResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getLanguage(), e.getPrimaryText(), e.getHeadline(),
                e.getDescription(), e.getCta(), e.getStatus(),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
