package com.amp.creatives;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a creative asset with its associated copy variants.
 * Used by the package builder UI to show available assets + variants.
 */
public record CreativeWithVariantsResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String assetType,
        String originalFilename,
        String status,
        Integer widthPx,
        Integer heightPx,
        Long sizeBytes,
        String mimeType,
        String thumbnailUrl,
        Double qualityScore,
        List<VariantSummary> copyVariants
) {

    public record VariantSummary(
            UUID id,
            String primaryText,
            String headline,
            String description,
            String cta,
            String language,
            String status
    ) {
        public static VariantSummary from(CopyVariant cv) {
            return new VariantSummary(
                    cv.getId(),
                    cv.getPrimaryText(),
                    cv.getHeadline(),
                    cv.getDescription(),
                    cv.getCta(),
                    cv.getLanguage(),
                    cv.getStatus()
            );
        }
    }

    public static CreativeWithVariantsResponse from(CreativeAsset asset, String thumbnailUrl,
                                                     Double qualityScore, List<CopyVariant> variants) {
        return new CreativeWithVariantsResponse(
                asset.getId(),
                asset.getAgencyId(),
                asset.getClientId(),
                asset.getAssetType(),
                asset.getOriginalFilename(),
                asset.getStatus(),
                asset.getWidthPx(),
                asset.getHeightPx(),
                asset.getSizeBytes(),
                asset.getMimeType(),
                thumbnailUrl,
                qualityScore,
                variants.stream().map(VariantSummary::from).toList()
        );
    }
}
