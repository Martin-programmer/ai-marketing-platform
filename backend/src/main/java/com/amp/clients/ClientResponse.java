package com.amp.clients;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a client.
 */
public record ClientResponse(
        UUID id,
        UUID agencyId,
        String name,
        String industry,
        String status,
        String timezone,
        String currency,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ClientResponse from(Client entity) {
        return new ClientResponse(
                entity.getId(),
                entity.getAgencyId(),
                entity.getName(),
                entity.getIndustry(),
                entity.getStatus(),
                entity.getTimezone(),
                entity.getCurrency(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
