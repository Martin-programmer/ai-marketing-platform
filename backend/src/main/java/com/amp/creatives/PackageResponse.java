package com.amp.creatives;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a creative package.
 */
public record PackageResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String name,
        String objective,
        String status,
        String notes,
        UUID createdBy,
        UUID approvedBy,
        OffsetDateTime createdAt,
        OffsetDateTime approvedAt
) {
    public static PackageResponse from(CreativePackage e) {
        return new PackageResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getName(), e.getObjective(), e.getStatus(),
                e.getNotes(), e.getCreatedBy(), e.getApprovedBy(),
                e.getCreatedAt(), e.getApprovedAt()
        );
    }
}
