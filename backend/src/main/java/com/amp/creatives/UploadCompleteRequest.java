package com.amp.creatives;

/**
 * Request DTO to mark an upload as complete.
 * Optional fields for checksum and image dimensions.
 */
public record UploadCompleteRequest(
        String checksumSha256,
        Integer widthPx,
        Integer heightPx
) {}
