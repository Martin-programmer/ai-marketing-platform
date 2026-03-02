package com.amp.creatives;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a creative asset.
 */
public record AssetResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String assetType,
        String s3Bucket,
        String s3Key,
        String originalFilename,
        String mimeType,
        Long sizeBytes,
        Integer widthPx,
        Integer heightPx,
        Integer durationMs,
        String checksumSha256,
        String status,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AssetResponse from(CreativeAsset e) {
        return new AssetResponse(
                e.getId(), e.getAgencyId(), e.getClientId(),
                e.getAssetType(), e.getS3Bucket(), e.getS3Key(),
                e.getOriginalFilename(), e.getMimeType(), e.getSizeBytes(),
                e.getWidthPx(), e.getHeightPx(), e.getDurationMs(),
                e.getChecksumSha256(), e.getStatus(), e.getCreatedBy(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
