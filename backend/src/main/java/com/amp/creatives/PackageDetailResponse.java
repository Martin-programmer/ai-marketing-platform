package com.amp.creatives;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a single creative package including its items with nested asset/variant data.
 */
public record PackageDetailResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String name,
        String objective,
        String status,
        String notes,
        UUID createdBy,
        UUID approvedBy,
        OffsetDateTime createdAt,
        OffsetDateTime approvedAt,
        int itemCount,
        List<PackageItemDetailResponse> items
) {
    public static PackageDetailResponse from(CreativePackage pkg, List<PackageItemDetailResponse> items) {
        return new PackageDetailResponse(
                pkg.getId(), pkg.getAgencyId(), pkg.getClientId(),
                pkg.getName(), pkg.getObjective(), pkg.getStatus(),
                pkg.getNotes(), pkg.getCreatedBy(), pkg.getApprovedBy(),
                pkg.getCreatedAt(), pkg.getApprovedAt(),
                items.size(), items
        );
    }

    /**
     * Enriched item response with nested creative asset and copy variant details.
     */
    public record PackageItemDetailResponse(
            UUID id,
            UUID packageId,
            String ctaType,
            String destinationUrl,
            Integer weight,
            OffsetDateTime createdAt,
            CreativeAssetDetail creativeAsset,
            CopyVariantDetail copyVariant,
            Double qualityScore
    ) {}

    public record CreativeAssetDetail(
            UUID id,
            String originalFilename,
            String assetType,
            String thumbnailUrl,
            String status,
            Integer widthPx,
            Integer heightPx,
            Long sizeBytes
    ) {}

    public record CopyVariantDetail(
            UUID id,
            String primaryText,
            String headline,
            String description,
            String ctaType,
            String language,
            String status
    ) {}
}
