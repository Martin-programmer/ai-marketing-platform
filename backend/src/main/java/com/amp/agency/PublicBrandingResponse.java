package com.amp.agency;

/**
 * Limited branding DTO for public (no-auth) endpoints.
 * Does not include sensitive agency data — only visual branding.
 */
public record PublicBrandingResponse(
        String name,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String tagline
) {}
