package com.amp.campaigns;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record CampaignPerformanceResponse(
        UUID campaignId,
        String campaignName,
        String status,
        BigDecimal spend,
        long impressions,
        long clicks,
        BigDecimal conversions,
        BigDecimal conversionValue,
        BigDecimal ctr,
        BigDecimal cpc,
        BigDecimal roas
) {
    public static CampaignPerformanceResponse from(CampaignPerformanceProjection row) {
        return new CampaignPerformanceResponse(
                row.getCampaignId(),
                row.getCampaignName(),
                row.getStatus(),
                scale(row.getSpend(), 2),
                row.getImpressions() != null ? row.getImpressions() : 0L,
                row.getClicks() != null ? row.getClicks() : 0L,
                scale(row.getConversions(), 2),
                scale(row.getConversionValue(), 2),
                scale(row.getCtr(), 2),
                scale(row.getCpc(), 2),
                scale(row.getRoas(), 4)
        );
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value != null
                ? value.setScale(scale, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
    }
}