package com.amp.insights;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read-only service for querying daily insights and KPI aggregations.
 */
@Service
@Transactional(readOnly = true)
public class InsightService {

    private final InsightDailyRepository repository;

    public InsightService(InsightDailyRepository repository) {
        this.repository = repository;
    }

    public List<InsightResponse> getClientInsights(UUID agencyId, UUID clientId,
                                                   LocalDate from, LocalDate to) {
        return repository.findAllByAgencyIdAndClientIdAndDateBetween(agencyId, clientId, from, to)
                .stream().map(InsightResponse::from).toList();
    }

    public List<InsightResponse> getCampaignInsights(UUID agencyId, UUID campaignId,
                                                     LocalDate from, LocalDate to) {
        return repository.findAllByAgencyIdAndCampaignIdAndDateBetween(agencyId, campaignId, from, to)
            .stream().map(row -> toCampaignInsightResponse(agencyId, row)).toList();
    }

    @Cacheable(value = "kpis", key = "#agencyId + '_' + #clientId + '_' + #from + '_' + #to")
    public KpiSummary getClientKpis(UUID agencyId, UUID clientId,
                                    LocalDate from, LocalDate to) {
        return repository.aggregateKpis(agencyId, clientId, from, to);
    }

    private InsightResponse toCampaignInsightResponse(UUID agencyId, CampaignDailyInsightProjection row) {
        return new InsightResponse(
                null,
                agencyId,
                row.getClientId(),
                "CAMPAIGN",
                row.getEntityId(),
                row.getDate(),
                decimalOrZero(row.getSpend()),
                row.getImpressions() != null ? row.getImpressions() : 0L,
                row.getClicks() != null ? row.getClicks() : 0L,
                decimalOrZero(row.getCtr()),
                decimalOrZero(row.getCpc()),
                decimalOrZero(row.getCpm()),
                decimalOrZero(row.getConversions()),
                decimalOrZero(row.getConversionValue()),
                decimalOrZero(row.getRoas()),
                decimalOrZero(row.getFrequency()),
                row.getReach() != null ? row.getReach() : 0L,
                null,
                (OffsetDateTime) null
        );
    }

    private BigDecimal decimalOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
