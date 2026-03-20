package com.amp.campaigns;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating a new campaign.
 */
public record CreateCampaignRequest(
        UUID clientId,
        @NotBlank(message = "name is required")
        String name,
        @NotBlank(message = "objective is required")
        String objective,
        String budgetType,
        BigDecimal dailyBudget
) {
        public CreateCampaignRequest(UUID clientId, String name, String objective) {
                this(clientId, name, objective, "ABO", null);
        }
}
