package com.amp.creatives;

import java.util.UUID;

/**
 * Response DTO from initiating an upload.
 * Contains the presigned PUT URL the frontend uses to upload directly to S3.
 */
public record UploadInitResponse(
        UUID assetId,
        String s3Key,
        String presignedPutUrl,
        String s3Bucket
) {}
