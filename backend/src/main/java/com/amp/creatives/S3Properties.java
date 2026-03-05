package com.amp.creatives;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for S3 creative asset storage.
 * Bound from {@code storage.s3.*} in application YAML.
 */
@Component
@ConfigurationProperties(prefix = "storage.s3")
public class S3Properties {

    private String bucket = "amp-creatives-staging";
    private String region = "eu-north-1";
    private int presignedUrlExpirationMinutes = 60;
    private boolean enabled = true;
    private String localStoragePath = "./uploads";

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public int getPresignedUrlExpirationMinutes() { return presignedUrlExpirationMinutes; }
    public void setPresignedUrlExpirationMinutes(int m) { this.presignedUrlExpirationMinutes = m; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getLocalStoragePath() { return localStoragePath; }
    public void setLocalStoragePath(String p) { this.localStoragePath = p; }
}
