package com.amp.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the email service (AWS SES).
 */
@Component
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    private String fromAddress = "noreply@adverion.xyz";
    private String fromName = "AI Marketing Platform";
    private String region = "eu-north-1";
    private boolean enabled = true;
    private String baseUrl = "https://adverion.xyz";

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
