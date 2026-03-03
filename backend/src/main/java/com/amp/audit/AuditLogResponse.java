package com.amp.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing an audit log entry.
 */
public record AuditLogResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        UUID actorUserId,
        String actorRole,
        String action,
        String entityType,
        UUID entityId,
        String beforeJson,
        String afterJson,
        String correlationId,
        String ip,
        String userAgent,
        OffsetDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog e) {
        return new AuditLogResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getActorUserId(), e.getActorRole(),
                e.getAction(), e.getEntityType(), e.getEntityId(),
                e.getBeforeJson(), e.getAfterJson(),
                e.getCorrelationId(), e.getIp(), e.getUserAgent(),
                e.getCreatedAt()
        );
    }
}
