package com.amp.campaigns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a new campaign.
 */
public record CreateCampaignRequest(
        @NotNull(message = "clientId is required")
        UUID clientId,
        @NotBlank(message = "name is required")
        String name,
        @NotBlank(message = "objective is required")
        String objective
) {
}
