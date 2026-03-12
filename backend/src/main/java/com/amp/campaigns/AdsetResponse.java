package com.amp.campaigns;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing an adset.
 */
public record AdsetResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        UUID campaignId,
        String metaAdsetId,
        String name,
        BigDecimal dailyBudget,
        String targetingJson,
        String optimizationGoal,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AdsetResponse from(Adset e) {
        return new AdsetResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getCampaignId(), e.getMetaAdsetId(),
                e.getName(), e.getDailyBudget(), e.getTargetingJson(), e.getOptimizationGoal(),
                e.getStatus(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
