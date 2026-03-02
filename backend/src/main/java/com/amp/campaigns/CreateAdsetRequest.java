package com.amp.campaigns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating an adset within a campaign.
 */
public record CreateAdsetRequest(
        @NotNull(message = "campaignId is required")
        UUID campaignId,
        @NotBlank(message = "name is required")
        String name,
        String targetingJson,
        @NotNull(message = "dailyBudget is required")
        BigDecimal dailyBudget,
        String optimizationGoal
) {
}
