package com.amp.insights;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface CampaignDailyInsightProjection {
    UUID getEntityId();
    UUID getClientId();
    LocalDate getDate();
    BigDecimal getSpend();
    Long getImpressions();
    Long getClicks();
    BigDecimal getCtr();
    BigDecimal getCpc();
    BigDecimal getCpm();
    BigDecimal getConversions();
    BigDecimal getConversionValue();
    BigDecimal getRoas();
    BigDecimal getFrequency();
    Long getReach();
}