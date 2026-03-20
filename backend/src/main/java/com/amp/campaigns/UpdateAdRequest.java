package com.amp.campaigns;

public record UpdateAdRequest(
        String name,
        String primaryText,
        String headline,
        String description,
        String ctaType,
        String destinationUrl,
        String status
) {
}