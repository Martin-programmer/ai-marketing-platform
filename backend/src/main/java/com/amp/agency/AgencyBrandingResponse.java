package com.amp.agency;

/**
 * Full branding DTO returned to AGENCY_ADMIN from GET /api/v1/agency/branding.
 */
public record AgencyBrandingResponse(
    String slug,
        String logoUrl,
        String logoS3Key,
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
) {

    public static AgencyBrandingResponse from(Agency a) {
        return new AgencyBrandingResponse(
                a.getSlug(),
                a.getLogoUrl(),
                a.getLogoS3Key(),
                a.getPrimaryColor(),
                a.getSecondaryColor(),
                a.getAccentColor(),
                a.getFontFamily(),
                a.getCompanyEmail(),
                a.getCompanyPhone(),
                a.getCompanyWebsite(),
                a.getCompanyAddress(),
                a.getEmailFooterText(),
                a.getReportFooterText(),
                a.getReportDisclaimer(),
                a.getPortalWelcomeMessage(),
                a.getCustomCss()
        );
    }
}
