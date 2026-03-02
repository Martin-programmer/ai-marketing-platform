package com.amp.campaigns;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a campaign.
 */
public record CampaignResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String platform,
        String metaCampaignId,
        String name,
        String objective,
        String status,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CampaignResponse from(Campaign e) {
        return new CampaignResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getPlatform(), e.getMetaCampaignId(),
                e.getName(), e.getObjective(), e.getStatus(),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
