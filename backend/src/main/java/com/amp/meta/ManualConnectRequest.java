package com.amp.meta;

public record ManualConnectRequest(
        String accessToken,
        String adAccountId,
        String pixelId,
        String pageId
) {}
