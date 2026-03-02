package com.amp.meta;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for MetaConnection — never exposes tokens.
 */
public record MetaConnectionResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String adAccountId,
        String pixelId,
        String pageId,
        String tokenKeyId,
        String status,
        OffsetDateTime connectedAt,
        OffsetDateTime lastSyncAt,
        String lastErrorCode,
        String lastErrorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static MetaConnectionResponse from(MetaConnection e) {
        return new MetaConnectionResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getAdAccountId(), e.getPixelId(), e.getPageId(),
                e.getTokenKeyId(), e.getStatus(),
                e.getConnectedAt(), e.getLastSyncAt(),
                e.getLastErrorCode(), e.getLastErrorMessage(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
