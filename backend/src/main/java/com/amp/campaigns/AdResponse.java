package com.amp.campaigns;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing an ad.
 */
public record AdResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        UUID adsetId,
        String metaAdId,
        String name,
        UUID creativePackageItemId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AdResponse from(Ad e) {
        return new AdResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getAdsetId(), e.getMetaAdId(),
                e.getName(), e.getCreativePackageItemId(),
                e.getStatus(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
