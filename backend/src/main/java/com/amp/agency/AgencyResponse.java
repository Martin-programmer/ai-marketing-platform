package com.amp.agency;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing an agency.
 */
public record AgencyResponse(
        UUID id,
        String name,
        String status,
        String planCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AgencyResponse from(Agency e) {
        return new AgencyResponse(
                e.getId(), e.getName(), e.getStatus(),
                e.getPlanCode(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
