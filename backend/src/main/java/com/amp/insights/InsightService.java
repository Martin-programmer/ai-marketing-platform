package com.amp.insights;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
                .stream().map(InsightResponse::from).toList();
    }

    public KpiSummary getClientKpis(UUID agencyId, UUID clientId,
                                    LocalDate from, LocalDate to) {
        return repository.aggregateKpis(agencyId, clientId, from, to);
    }
}
