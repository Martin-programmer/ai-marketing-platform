package com.amp.campaigns;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service handling campaign, adset and ad operations.
 */
@Service
@Transactional
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final AdsetRepository adsetRepository;
    private final AdRepository adRepository;
    private final AuditService auditService;

    public CampaignService(CampaignRepository campaignRepository,
                           AdsetRepository adsetRepository,
                           AdRepository adRepository,
                           AuditService auditService) {
        this.campaignRepository = campaignRepository;
        this.adsetRepository = adsetRepository;
        this.adRepository = adRepository;
        this.auditService = auditService;
    }

    // ──────── Campaign ────────

    @Transactional(readOnly = true)
    public List<Campaign> listCampaigns(UUID agencyId, UUID clientId) {
        return campaignRepository.findAllByAgencyIdAndClientId(agencyId, clientId);
    }

    public Campaign createCampaign(UUID agencyId, CreateCampaignRequest req) {
        TenantContext ctx = TenantContextHolder.require();

        Campaign c = new Campaign();
        c.setAgencyId(agencyId);
        c.setClientId(req.clientId());
        c.setPlatform("META");
        c.setName(req.name());
        c.setObjective(req.objective());
        c.setStatus("DRAFT");
        c.setCreatedBy(ctx.getUserId());
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());

        Campaign saved = campaignRepository.save(c);

        auditService.log(agencyId, req.clientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.CAMPAIGN_CREATE, "Campaign", saved.getId(),
                null, saved, null);

        return saved;
    }

    @Transactional(readOnly = true)
    public Campaign getCampaign(UUID agencyId, UUID campaignId) {
        return campaignRepository.findByIdAndAgencyId(campaignId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
    }

    public Campaign pauseCampaign(UUID agencyId, UUID campaignId) {
        TenantContext ctx = TenantContextHolder.require();
        Campaign c = getCampaign(agencyId, campaignId);
        String before = c.getStatus();
        c.setStatus("PAUSED");
        Campaign saved = campaignRepository.save(c);

        auditService.log(agencyId, c.getClientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.CAMPAIGN_PAUSE, "Campaign", campaignId,
                before, saved.getStatus(), null);

        return saved;
    }

    public Campaign resumeCampaign(UUID agencyId, UUID campaignId) {
        TenantContext ctx = TenantContextHolder.require();
        Campaign c = getCampaign(agencyId, campaignId);
        String before = c.getStatus();
        c.setStatus("PUBLISHED");
        Campaign saved = campaignRepository.save(c);

        auditService.log(agencyId, c.getClientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.CAMPAIGN_RESUME, "Campaign", campaignId,
                before, saved.getStatus(), null);

        return saved;
    }

    public Campaign publishCampaign(UUID agencyId, UUID campaignId) {
        TenantContext ctx = TenantContextHolder.require();
        Campaign c = getCampaign(agencyId, campaignId);

        if (!"DRAFT".equals(c.getStatus())) {
            throw new IllegalStateException("Only DRAFT campaigns can be published");
        }

        String before = c.getStatus();
        c.setStatus("PUBLISHED");
        Campaign saved = campaignRepository.save(c);

        auditService.log(agencyId, c.getClientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.CAMPAIGN_PUBLISH, "Campaign", campaignId,
                before, saved.getStatus(), null);

        return saved;
    }

    // ──────── Adset ────────

    @Transactional(readOnly = true)
    public List<Adset> listAdsets(UUID campaignId) {
        return adsetRepository.findAllByCampaignId(campaignId);
    }

    public Adset createAdset(UUID agencyId, CreateAdsetRequest req) {
        TenantContext ctx = TenantContextHolder.require();

        // Verify campaign belongs to agency
        Campaign campaign = getCampaign(agencyId, req.campaignId());

        Adset a = new Adset();
        a.setAgencyId(agencyId);
        a.setClientId(campaign.getClientId());
        a.setCampaignId(req.campaignId());
        a.setName(req.name());
        a.setDailyBudget(req.dailyBudget());
        a.setTargetingJson(req.targetingJson() != null ? req.targetingJson() : "{}");
        a.setStatus("DRAFT");
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());

        Adset saved = adsetRepository.save(a);

        auditService.log(agencyId, campaign.getClientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.ADSET_CREATE, "Adset", saved.getId(),
                null, saved, null);

        return saved;
    }

    // ──────── Ad ────────

    @Transactional(readOnly = true)
    public List<Ad> listAds(UUID adsetId) {
        return adRepository.findAllByAdsetId(adsetId);
    }

    public Ad createAd(UUID agencyId, CreateAdRequest req) {
        TenantContext ctx = TenantContextHolder.require();

        // Verify adset belongs to agency
        Adset adset = adsetRepository.findByIdAndAgencyId(req.adsetId(), agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Adset", req.adsetId()));

        Ad ad = new Ad();
        ad.setAgencyId(agencyId);
        ad.setClientId(adset.getClientId());
        ad.setAdsetId(req.adsetId());
        ad.setName(req.name());
        ad.setCreativePackageItemId(req.creativePackageItemId());
        ad.setStatus("DRAFT");
        ad.setCreatedAt(OffsetDateTime.now());
        ad.setUpdatedAt(OffsetDateTime.now());

        Ad saved = adRepository.save(ad);

        auditService.log(agencyId, adset.getClientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.AD_CREATE, "Ad", saved.getId(),
                null, saved, null);

        return saved;
    }
}
