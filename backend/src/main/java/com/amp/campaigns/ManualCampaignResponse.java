package com.amp.campaigns;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response for the full manual campaign creation — includes campaign + adsets + ads with IDs.
 */
public record ManualCampaignResponse(
        UUID campaignId,
        String name,
        String objective,
        String platform,
        String status,
        List<AdsetResult> adsets
) {
    public record AdsetResult(
            UUID adsetId,
            String name,
            BigDecimal dailyBudget,
            String optimizationGoal,
            String targetingJson,
            String status,
            List<AdResult> ads
    ) {}

    public record AdResult(
            UUID adId,
            String name,
            UUID creativeAssetId,
            UUID copyVariantId,
            String primaryText,
            String headline,
            String description,
            String cta,
            String destinationUrl,
            String status
    ) {}
}
