package com.amp.insights;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a daily insight row.
 */
public record InsightResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String entityType,
        UUID entityId,
        LocalDate date,
        BigDecimal spend,
        long impressions,
        long clicks,
        BigDecimal ctr,
        BigDecimal cpc,
        BigDecimal cpm,
        BigDecimal conversions,
        BigDecimal conversionValue,
        BigDecimal roas,
        BigDecimal frequency,
        long reach,
        String rawJson,
        OffsetDateTime createdAt
) {
    public static InsightResponse from(InsightDaily e) {
        return new InsightResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getEntityType(), e.getEntityId(),
                e.getDate(), e.getSpend(),
                e.getImpressions(), e.getClicks(),
                e.getCtr(), e.getCpc(), e.getCpm(),
                e.getConversions(), e.getConversionValue(),
                e.getRoas(), e.getFrequency(), e.getReach(),
                e.getRawJson(), e.getCreatedAt()
        );
    }
}
