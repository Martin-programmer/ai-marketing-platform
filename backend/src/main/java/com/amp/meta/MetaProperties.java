package com.amp.meta;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "meta")
public class MetaProperties {

    private String appId;
    private String appSecret;
    private String redirectUri;
    private String graphApiVersion = "v19.0";
    private String graphApiBaseUrl = "https://graph.facebook.com";

    // Standard getters and setters for all fields
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getGraphApiVersion() { return graphApiVersion; }
    public void setGraphApiVersion(String v) { this.graphApiVersion = v; }

    public String getGraphApiBaseUrl() { return graphApiBaseUrl; }
    public void setGraphApiBaseUrl(String u) { this.graphApiBaseUrl = u; }

    public String getOauthAuthorizeUrl() {
        return "https://www.facebook.com/" + graphApiVersion + "/dialog/oauth";
    }

    public String getGraphUrl(String path) {
        return graphApiBaseUrl + "/" + graphApiVersion + "/" + path;
    }
}
