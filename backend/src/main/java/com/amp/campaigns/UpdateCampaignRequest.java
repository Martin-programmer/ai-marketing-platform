package com.amp.campaigns;

public record UpdateCampaignRequest(
        String name,
        String status
) {
}