package com.amp.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Nested DTO representing a full AI-generated campaign proposal.
 */
public record CampaignProposalResponse(
        UUID campaignId,
        String campaignName,
        String objective,
        String platform,
        String status,
        String rationale,
        BigDecimal suggestedDailyBudget,
        String estimatedResults,
        List<String> warnings,
        List<ProposedAdset> adsets
) {

    public record ProposedAdset(
            UUID adsetId,
            String name,
            BigDecimal dailyBudget,
            String targetingJson,
            String optimizationGoal,
            List<ProposedAd> ads
    ) {}

    public record ProposedAd(
            UUID adId,
            String name,
            UUID creativeAssetId,
            String primaryText,
            String headline,
            String description,
            String cta,
            String url
    ) {}
}
