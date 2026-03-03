package com.amp.insights;

import java.math.BigDecimal;

/**
 * Aggregated KPI summary for a date range.
 * Used as a JPQL projection — requires an all-args constructor.
 */
public class KpiSummary {

    private Long totalImpressions;
    private Long totalClicks;
    private BigDecimal totalSpend;
    private BigDecimal totalConversions;
    private Double avgCtr;
    private BigDecimal avgCpc;
    private BigDecimal avgRoas;

    protected KpiSummary() {}

    public KpiSummary(Long totalImpressions,
                      Long totalClicks,
                      BigDecimal totalSpend,
                      BigDecimal totalConversions,
                      Double avgCtr,
                      BigDecimal avgCpc,
                      BigDecimal avgRoas) {
        this.totalImpressions = totalImpressions;
        this.totalClicks = totalClicks;
        this.totalSpend = totalSpend;
        this.totalConversions = totalConversions;
        this.avgCtr = avgCtr;
        this.avgCpc = avgCpc;
        this.avgRoas = avgRoas;
    }

    public Long getTotalImpressions() { return totalImpressions; }
    public Long getTotalClicks() { return totalClicks; }
    public BigDecimal getTotalSpend() { return totalSpend; }
    public BigDecimal getTotalConversions() { return totalConversions; }
    public Double getAvgCtr() { return avgCtr; }
    public BigDecimal getAvgCpc() { return avgCpc; }
    public BigDecimal getAvgRoas() { return avgRoas; }
}
