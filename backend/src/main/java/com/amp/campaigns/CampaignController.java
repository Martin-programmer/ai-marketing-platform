package com.amp.campaigns;

import com.amp.tenancy.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for campaign, adset and ad operations.
 */
@RestController
@RequestMapping("/api/v1")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    // ──────── Campaign ────────

    @GetMapping("/clients/{clientId}/campaigns")
    public List<CampaignResponse> listCampaigns(@PathVariable UUID clientId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return campaignService.listCampaigns(agencyId, clientId)
                .stream().map(CampaignResponse::from).toList();
    }

    @PostMapping("/clients/{clientId}/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignResponse createCampaign(@PathVariable UUID clientId,
                                           @Valid @RequestBody CreateCampaignRequest req) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return CampaignResponse.from(campaignService.createCampaign(agencyId, clientId, req));
    }

    @GetMapping("/campaigns/{campaignId}")
    public CampaignResponse getCampaign(@PathVariable UUID campaignId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return CampaignResponse.from(campaignService.getCampaign(agencyId, campaignId));
    }

    @PostMapping("/campaigns/{campaignId}/pause")
    public CampaignResponse pauseCampaign(@PathVariable UUID campaignId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return CampaignResponse.from(campaignService.pauseCampaign(agencyId, campaignId));
    }

    @PostMapping("/campaigns/{campaignId}/resume")
    public CampaignResponse resumeCampaign(@PathVariable UUID campaignId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return CampaignResponse.from(campaignService.resumeCampaign(agencyId, campaignId));
    }

    @PostMapping("/campaigns/{campaignId}/publish")
    public CampaignResponse publishCampaign(@PathVariable UUID campaignId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return CampaignResponse.from(campaignService.publishCampaign(agencyId, campaignId));
    }

    // ──────── Adset ────────

    @GetMapping("/campaigns/{campaignId}/adsets")
    public List<AdsetResponse> listAdsets(@PathVariable UUID campaignId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        // Verify campaign belongs to agency
        campaignService.getCampaign(agencyId, campaignId);
        return campaignService.listAdsets(campaignId)
                .stream().map(AdsetResponse::from).toList();
    }

    @PostMapping("/campaigns/{campaignId}/adsets")
    @ResponseStatus(HttpStatus.CREATED)
    public AdsetResponse createAdset(@PathVariable UUID campaignId,
                                     @Valid @RequestBody CreateAdsetRequest req) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return AdsetResponse.from(campaignService.createAdset(agencyId, campaignId, req));
    }

    // ──────── Ad ────────

    @GetMapping("/adsets/{adsetId}/ads")
    public List<AdResponse> listAds(@PathVariable UUID adsetId) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        // Verify adset belongs to agency
        campaignService.listAds(adsetId);
        return campaignService.listAds(adsetId)
                .stream().map(AdResponse::from).toList();
    }

    @PostMapping("/adsets/{adsetId}/ads")
    @ResponseStatus(HttpStatus.CREATED)
    public AdResponse createAd(@PathVariable UUID adsetId,
                               @Valid @RequestBody CreateAdRequest req) {
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        return AdResponse.from(campaignService.createAd(agencyId, adsetId, req));
    }
}
