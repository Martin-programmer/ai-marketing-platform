package com.amp.creatives;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PackageItemResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        UUID packageId,
        UUID creativeAssetId,
        UUID copyVariantId,
        String ctaType,
        String destinationUrl,
        Integer weight,
        OffsetDateTime createdAt,
        AssetResponse creativeAsset,
        CopyVariantResponse copyVariant,
        Double qualityScore
) {
}