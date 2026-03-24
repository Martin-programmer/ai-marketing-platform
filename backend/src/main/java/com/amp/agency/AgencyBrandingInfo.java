package com.amp.agency;

/**
 * Internal record carrying agency branding context for email and report rendering.
 * Not exposed directly via API — used as a parameter object.
 */
public record AgencyBrandingInfo(
        String agencyName,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String accentColor,
        String fontFamily,
        String companyEmail,
        String emailFooterText,
        String reportFooterText,
        String reportDisclaimer,
        String portalWelcomeMessage
) {

    /**
     * Build from an Agency entity, falling back to defaults for null fields.
     */
    public static AgencyBrandingInfo from(Agency agency) {
        return new AgencyBrandingInfo(
                agency.getName(),
                agency.getLogoUrl(),
                agency.getPrimaryColor() != null ? agency.getPrimaryColor() : "#1976D2",
                agency.getSecondaryColor() != null ? agency.getSecondaryColor() : "#424242",
                agency.getAccentColor() != null ? agency.getAccentColor() : "#FF9800",
                agency.getFontFamily() != null ? agency.getFontFamily() : "Roboto, sans-serif",
                agency.getCompanyEmail(),
                agency.getEmailFooterText(),
                agency.getReportFooterText(),
                agency.getReportDisclaimer(),
                agency.getPortalWelcomeMessage()
        );
    }
}
