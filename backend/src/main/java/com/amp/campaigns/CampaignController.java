package com.amp.campaigns;

import com.amp.ai.CampaignCreatorService;
import com.amp.ai.CampaignProposalResponse;
import com.amp.ai.ExecutorService;
import com.amp.ai.PerformanceOptimizerService;
import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.tenancy.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for campaign, adset and ad operations.
 */
@RestController
@RequestMapping("/api/v1")
public class CampaignController {

    private final CampaignService campaignService;
    private final AccessControl accessControl;
    private final CampaignCreatorService campaignCreatorService;
    private final ExecutorService executorService;
    private final PerformanceOptimizerService performanceOptimizerService;

    public CampaignController(CampaignService campaignService,
                              AccessControl accessControl,
                              CampaignCreatorService campaignCreatorService,
                              ExecutorService executorService,
                              PerformanceOptimizerService performanceOptimizerService) {
        this.campaignService = campaignService;
        this.accessControl = accessControl;
        this.campaignCreatorService = campaignCreatorService;
        this.executorService = executorService;
        this.performanceOptimizerService = performanceOptimizerService;
    }

    private UUID agencyId() {
        return TenantContextHolder.require().getAgencyId();
    }

    // ──────── Campaign ────────

    @GetMapping("/clients/{clientId}/campaigns")
    public List<CampaignResponse> listCampaigns(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_VIEW);
        UUID agencyId = agencyId();
        return campaignService.listCampaigns(agencyId, clientId)
                .stream().map(CampaignResponse::from).toList();
    }

    @PostMapping("/clients/{clientId}/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignResponse createCampaign(@PathVariable UUID clientId,
                                           @Valid @RequestBody CreateCampaignRequest req) {
        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_EDIT);
        UUID agencyId = agencyId();
        return CampaignResponse.from(campaignService.createCampaign(agencyId, clientId, req));
    }

    @PostMapping("/clients/{clientId}/campaigns/create")
    @ResponseStatus(HttpStatus.CREATED)
    public ManualCampaignResponse createFullCampaign(@PathVariable UUID clientId,
                                                      @Valid @RequestBody ManualCampaignRequest req) {
        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_EDIT);
        UUID agencyId = agencyId();
        return campaignService.createFullCampaign(agencyId, clientId, req);
    }

    @GetMapping("/campaigns/{campaignId}")
    public CampaignResponse getCampaign(@PathVariable UUID campaignId) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.CAMPAIGNS_VIEW);
        return CampaignResponse.from(campaign);
    }

    @PatchMapping("/campaigns/{campaignId}")
    public CampaignResponse updateCampaign(@PathVariable UUID campaignId,
                                           @RequestBody UpdateCampaignRequest req) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.CAMPAIGNS_EDIT);
        return CampaignResponse.from(campaignService.updateCampaign(agencyId, campaignId, req));
    }

    @PostMapping("/campaigns/{campaignId}/pause")
    public CampaignResponse pauseCampaign(@PathVariable UUID campaignId) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.CAMPAIGNS_EDIT);
        return CampaignResponse.from(campaignService.pauseCampaign(agencyId, campaignId));
    }

    @PostMapping("/campaigns/{campaignId}/resume")
    public CampaignResponse resumeCampaign(@PathVariable UUID campaignId) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.CAMPAIGNS_EDIT);
        return CampaignResponse.from(campaignService.resumeCampaign(agencyId, campaignId));
    }

    @PostMapping("/campaigns/{campaignId}/publish")
    public ResponseEntity<?> publishCampaign(@PathVariable UUID campaignId) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.CAMPAIGNS_PUBLISH);
        var result = executorService.publishCampaign(agencyId, campaignId);
        if ("FAILED".equals(result.get("status"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/campaigns/{campaignId}/ai-analyze")
    public ResponseEntity<?> aiAnalyzeCampaign(@PathVariable UUID campaignId) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.AI_VIEW);
        return ResponseEntity.ok(performanceOptimizerService.runForCampaign(agencyId, campaign.getClientId(), campaignId));
    }

    // ──────── AI Campaign Proposal ────────

    /**
     * Generate an AI campaign proposal using Claude Opus.
     */
    @PostMapping("/clients/{clientId}/campaigns/ai-propose")
    public ResponseEntity<CampaignProposalResponse> aiPropose(
            @PathVariable UUID clientId,
            @RequestBody(required = false) AiCampaignProposalRequest body) {
        accessControl.requireClientPermission(clientId, Permission.CAMPAIGNS_EDIT);
        UUID agencyId = agencyId();
        String brief = body != null ? body.brief() : "";
        String budgetType = body != null ? body.budgetType() : null;
        java.math.BigDecimal dailyBudget = body != null ? body.dailyBudget() : null;
        CampaignProposalResponse proposal = campaignCreatorService.generateProposal(
                agencyId, clientId, brief, budgetType, dailyBudget);
        return ResponseEntity.ok(proposal);
    }

    /**
     * Publish a DRAFT campaign to Meta via the Executor (creates in Meta API).
     */
    @PostMapping("/campaigns/{campaignId}/meta-publish")
    public ResponseEntity<?> metaPublish(@PathVariable UUID campaignId) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.CAMPAIGNS_PUBLISH);
        var result = executorService.publishCampaign(agencyId, campaignId);
        if ("FAILED".equals(result.get("status"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
        return ResponseEntity.ok(result);
    }

    // ──────── Adset ────────

    @GetMapping("/campaigns/{campaignId}/adsets")
    public List<AdsetResponse> listAdsets(@PathVariable UUID campaignId) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.CAMPAIGNS_VIEW);
        return campaignService.listAdsets(campaignId)
                .stream().map(AdsetResponse::from).toList();
    }

    @PostMapping("/campaigns/{campaignId}/adsets")
    @ResponseStatus(HttpStatus.CREATED)
    public AdsetResponse createAdset(@PathVariable UUID campaignId,
                                     @Valid @RequestBody CreateAdsetRequest req) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.CAMPAIGNS_EDIT);
        return AdsetResponse.from(campaignService.createAdset(agencyId, campaignId, req));
    }

    @PatchMapping("/campaigns/{campaignId}/adsets/{adsetId}")
    public AdsetResponse updateAdset(@PathVariable UUID campaignId,
                                     @PathVariable UUID adsetId,
                                     @RequestBody UpdateAdsetRequest req) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.CAMPAIGNS_EDIT);
        return AdsetResponse.from(campaignService.updateAdset(agencyId, campaignId, adsetId, req));
    }

    // ──────── Ad ────────

    @GetMapping("/adsets/{adsetId}/ads")
    public List<AdResponse> listAds(@PathVariable UUID adsetId) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        return campaignService.listAds(agencyId, adsetId)
                .stream().map(AdResponse::from).toList();
    }

    @PostMapping("/adsets/{adsetId}/ads")
    @ResponseStatus(HttpStatus.CREATED)
    public AdResponse createAd(@PathVariable UUID adsetId,
                               @Valid @RequestBody CreateAdRequest req) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        return AdResponse.from(campaignService.createAd(agencyId, adsetId, req));
    }

    @PatchMapping("/campaigns/{campaignId}/adsets/{adsetId}/ads/{adId}")
    public AdResponse updateAd(@PathVariable UUID campaignId,
                               @PathVariable UUID adsetId,
                               @PathVariable UUID adId,
                               @RequestBody UpdateAdRequest req) {
        accessControl.requireAgencyRole();
        UUID agencyId = agencyId();
        Campaign campaign = campaignService.getCampaign(agencyId, campaignId);
        accessControl.requireClientPermission(campaign.getClientId(), Permission.CAMPAIGNS_EDIT);
        return AdResponse.from(campaignService.updateAd(agencyId, campaignId, adsetId, adId, req));
    }
}
