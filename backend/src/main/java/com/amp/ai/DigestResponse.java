package com.amp.ai;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight response DTO for weekly digest list (excludes full HTML content).
 */
public record DigestResponse(
        UUID id,
        UUID clientId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String subjectLine,
        OffsetDateTime sentAt,
        OffsetDateTime createdAt
) {
    public static DigestResponse from(AiWeeklyDigest d) {
        return new DigestResponse(
                d.getId(), d.getClientId(),
                d.getPeriodStart(), d.getPeriodEnd(),
                d.getSubjectLine(), d.getSentAt(), d.getCreatedAt());
    }
}
