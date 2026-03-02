package com.amp.campaigns;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request DTO for creating a new campaign.
 */
public record CreateCampaignRequest(
        UUID clientId,
        @NotBlank(message = "name is required")
        String name,
        @NotBlank(message = "objective is required")
        String objective
) {
}
