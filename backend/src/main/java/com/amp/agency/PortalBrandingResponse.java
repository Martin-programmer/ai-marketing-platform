package com.amp.agency;

/**
 * Branding DTO returned to CLIENT_USER from GET /api/v1/portal/branding.
 * Contains agency branding for the client's portal experience.
 */
public record PortalBrandingResponse(
        String agencyName,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String accentColor,
        String fontFamily,
        String portalWelcomeMessage,
        String poweredByText,
        boolean poweredByVisible
) {}
