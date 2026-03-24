package com.amp.agency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code agency} table (V001).
 */
@Entity
@Table(name = "agency")
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(name = "two_factor_enabled")
    private boolean twoFactorEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ── Branding fields (V028) ──

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "logo_s3_key")
    private String logoS3Key;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "secondary_color")
    private String secondaryColor;

    @Column(name = "accent_color")
    private String accentColor;

    @Column(name = "font_family")
    private String fontFamily;

    @Column(name = "company_email")
    private String companyEmail;

    @Column(name = "company_phone")
    private String companyPhone;

    @Column(name = "company_website")
    private String companyWebsite;

    @Column(name = "company_address")
    private String companyAddress;

    @Column(name = "email_footer_text")
    private String emailFooterText;

    @Column(name = "report_footer_text")
    private String reportFooterText;

    @Column(name = "report_disclaimer")
    private String reportDisclaimer;

    @Column(name = "portal_welcome_message")
    private String portalWelcomeMessage;

    @Column(name = "custom_css")
    private String customCss;

    @Column(name = "slug", unique = true)
    private String slug;

    public Agency() {}

    @PreUpdate
    private void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    // ---- getters & setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }

    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ── Branding getters & setters ──

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getLogoS3Key() { return logoS3Key; }
    public void setLogoS3Key(String logoS3Key) { this.logoS3Key = logoS3Key; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public String getCompanyEmail() { return companyEmail; }
    public void setCompanyEmail(String companyEmail) { this.companyEmail = companyEmail; }

    public String getCompanyPhone() { return companyPhone; }
    public void setCompanyPhone(String companyPhone) { this.companyPhone = companyPhone; }

    public String getCompanyWebsite() { return companyWebsite; }
    public void setCompanyWebsite(String companyWebsite) { this.companyWebsite = companyWebsite; }

    public String getCompanyAddress() { return companyAddress; }
    public void setCompanyAddress(String companyAddress) { this.companyAddress = companyAddress; }

    public String getEmailFooterText() { return emailFooterText; }
    public void setEmailFooterText(String emailFooterText) { this.emailFooterText = emailFooterText; }

    public String getReportFooterText() { return reportFooterText; }
    public void setReportFooterText(String reportFooterText) { this.reportFooterText = reportFooterText; }

    public String getReportDisclaimer() { return reportDisclaimer; }
    public void setReportDisclaimer(String reportDisclaimer) { this.reportDisclaimer = reportDisclaimer; }

    public String getPortalWelcomeMessage() { return portalWelcomeMessage; }
    public void setPortalWelcomeMessage(String portalWelcomeMessage) { this.portalWelcomeMessage = portalWelcomeMessage; }

    public String getCustomCss() { return customCss; }
    public void setCustomCss(String customCss) { this.customCss = customCss; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
}
