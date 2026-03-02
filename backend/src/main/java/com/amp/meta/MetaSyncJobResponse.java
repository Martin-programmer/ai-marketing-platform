package com.amp.meta;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a Meta sync job.
 */
public record MetaSyncJobResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String jobType,
        String jobStatus,
        String idempotencyKey,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String statsJson,
        String errorJson
) {
    public static MetaSyncJobResponse from(MetaSyncJob e) {
        return new MetaSyncJobResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getJobType(), e.getJobStatus(), e.getIdempotencyKey(),
                e.getRequestedAt(), e.getStartedAt(), e.getFinishedAt(),
                e.getStatsJson(), e.getErrorJson()
        );
    }
}
