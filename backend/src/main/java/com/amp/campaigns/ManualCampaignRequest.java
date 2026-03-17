package com.amp.campaigns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Full manual campaign creation request — campaign + adsets + ads in one call.
 */
public record ManualCampaignRequest(
        @NotBlank(message = "name is required")
        String name,
        @NotBlank(message = "objective is required")
        String objective,
        List<String> specialAdCategories,
        @NotEmpty(message = "at least one adset is required")
        @Valid
        List<AdsetPayload> adsets
) {
    public record AdsetPayload(
            @NotBlank(message = "adset name is required")
            String name,
            @NotNull(message = "dailyBudget is required")
            BigDecimal dailyBudget,
            String optimizationGoal,
            String billingEvent,
            String startDate,
            String endDate,
            Object targeting,
            String placements,
            @NotEmpty(message = "at least one ad is required")
            @Valid
            List<AdPayload> ads
    ) {}

    public record AdPayload(
            @NotBlank(message = "ad name is required")
            String name,
            UUID creativeAssetId,
            UUID copyVariantId,
            String primaryText,
            String headline,
            String description,
            String ctaType,
            String destinationUrl
    ) {}
}
