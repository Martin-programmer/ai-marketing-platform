package com.amp.campaigns;

import java.math.BigDecimal;
import java.util.UUID;

public interface CampaignPerformanceProjection {
    UUID getCampaignId();
    String getCampaignName();
    String getStatus();
    BigDecimal getSpend();
    Long getImpressions();
    Long getClicks();
    BigDecimal getConversions();
    BigDecimal getConversionValue();
    BigDecimal getCtr();
    BigDecimal getCpc();
    BigDecimal getRoas();
}