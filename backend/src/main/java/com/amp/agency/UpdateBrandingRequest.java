package com.amp.agency;

/**
 * Request DTO for PUT /api/v1/agency/branding.
 */
public record UpdateBrandingRequest(
        String slug,
        String primaryColor,
        String secondaryColor,
        String accentColor,
        String fontFamily,
        String companyEmail,
        String companyPhone,
        String companyWebsite,
        String companyAddress,
        String emailFooterText,
        String reportFooterText,
        String reportDisclaimer,
        String portalWelcomeMessage,
        String customCss
) {}
