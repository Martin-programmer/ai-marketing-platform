package com.amp.insights;

import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for daily insight queries and KPI aggregations.
 */
@RestController
@RequestMapping("/api/v1")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/clients/{clientId}/insights")
    public List<InsightResponse> getClientInsights(
            @PathVariable UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return insightService.getClientInsights(agencyId, clientId, from, to);
    }

    @GetMapping("/campaigns/{campaignId}/insights")
    public List<InsightResponse> getCampaignInsights(
            @PathVariable UUID campaignId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return insightService.getCampaignInsights(agencyId, campaignId, from, to);
    }

    @GetMapping("/clients/{clientId}/kpis")
    public KpiSummary getClientKpis(
            @PathVariable UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return insightService.getClientKpis(agencyId, clientId, from, to);
    }
}
