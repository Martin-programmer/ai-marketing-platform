package com.amp.campaigns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating an adset within a campaign.
 */
public record CreateAdsetRequest(
        UUID campaignId,
        @NotBlank(message = "name is required")
        String name,
        String targetingJson,
        @NotNull(message = "dailyBudget is required")
        BigDecimal dailyBudget,
        String optimizationGoal,
                String conversionEvent,
        String startDate,
        String endDate
) {
        public CreateAdsetRequest(UUID campaignId,
                                                          String name,
                                                          String targetingJson,
                                                          BigDecimal dailyBudget,
                                                          String optimizationGoal) {
                this(campaignId, name, targetingJson, dailyBudget, optimizationGoal, null, null, null);
        }

        public CreateAdsetRequest(UUID campaignId,
                                                          String name,
                                                          String targetingJson,
                                                          BigDecimal dailyBudget,
                                                          String optimizationGoal,
                                                          String startDate,
                                                          String endDate) {
                this(campaignId, name, targetingJson, dailyBudget, optimizationGoal, null, startDate, endDate);
        }
}
