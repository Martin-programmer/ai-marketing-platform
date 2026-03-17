package com.amp.campaigns;

import com.amp.ai.ExecutorService;
import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.clients.ClientRepository;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.meta.MetaConnection;
import com.amp.meta.MetaConnectionRepository;
import com.amp.meta.MetaGraphApiClient;
import com.amp.meta.MetaService;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service handling campaign, adset and ad operations.
 */
@Service
@Transactional
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaignRepository;
    private final AdsetRepository adsetRepository;
    private final AdRepository adRepository;
    private final AuditService auditService;
    private final ClientRepository clientRepository;
    private final MetaConnectionRepository metaConnectionRepository;
    private final MetaService metaService;
    private final MetaGraphApiClient metaGraphApiClient;
    private final ExecutorService executorService;
    private final ObjectMapper objectMapper;

    public CampaignService(CampaignRepository campaignRepository,
                           AdsetRepository adsetRepository,
                           AdRepository adRepository,
                           AuditService auditService,
                           ClientRepository clientRepository,
                           MetaConnectionRepository metaConnectionRepository,
                           MetaService metaService,
                           MetaGraphApiClient metaGraphApiClient,
                           ExecutorService executorService,
                           ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.adsetRepository = adsetRepository;
        this.adRepository = adRepository;
        this.auditService = auditService;
        this.clientRepository = clientRepository;
        this.metaConnectionRepository = metaConnectionRepository;
        this.metaService = metaService;
        this.metaGraphApiClient = metaGraphApiClient;
        this.executorService = executorService;
        this.objectMapper = objectMapper;
    }

    // ──────── Campaign ────────

    @Transactional(readOnly = true)
    @Cacheable(value = "campaigns", key = "#agencyId + '_' + #clientId")
    public List<Campaign> listCampaigns(UUID agencyId, UUID clientId) {
        log.info("Fetching campaigns for agency={}, client={}", agencyId, clientId);
        List<Campaign> campaigns = campaignRepository.findAllByAgencyIdAndClientId(agencyId, clientId);
        log.info("Found {} campaigns for agency={}, client={}", campaigns.size(), agencyId, clientId);
        return campaigns;
    }

    @Transactional(readOnly = true)
    public List<CampaignPerformanceResponse> listCampaignPerformance(UUID agencyId, UUID clientId,
                                                                     LocalDate from, LocalDate to) {
        log.info("Fetching campaign performance for agency={}, client={}, from={}, to={}",
                agencyId, clientId, from, to);
        return campaignRepository.findCampaignPerformance(agencyId, clientId, from, to)
                .stream()
                .map(CampaignPerformanceResponse::from)
                .toList();
    }

    @CacheEvict(value = "campaigns", key = "#agencyId + '_' + #clientId")
    public Campaign createCampaign(UUID agencyId, UUID clientId, CreateCampaignRequest req) {
        TenantContext ctx = TenantContextHolder.require();
        log.info("Creating campaign: name={}, objective={}, clientId={}", req.name(), req.objective(), clientId);

        // Verify client belongs to agency
        clientRepository.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        Campaign c = new Campaign();
        c.setAgencyId(agencyId);
        c.setClientId(clientId);
        c.setPlatform("META");
        c.setName(req.name());
        c.setObjective(req.objective());
        c.setStatus("DRAFT");
        c.setCreatedBy(ctx.getUserId());
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());

        Campaign saved = campaignRepository.save(c);

        auditService.log(agencyId, clientId, ctx.getUserId(), ctx.getRole(),
                AuditAction.CAMPAIGN_CREATE, "Campaign", saved.getId(),
                null, saved, null);

        return saved;
    }

    @Transactional(readOnly = true)
    public Campaign getCampaign(UUID agencyId, UUID campaignId) {
        return campaignRepository.findByIdAndAgencyId(campaignId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
    }

    @CacheEvict(value = "campaigns", allEntries = true)
    public Campaign pauseCampaign(UUID agencyId, UUID campaignId) {
        return updateCampaignStatus(agencyId, campaignId, "PAUSED", "PAUSED", AuditAction.CAMPAIGN_PAUSE);
    }

    @CacheEvict(value = "campaigns", allEntries = true)
    public Campaign resumeCampaign(UUID agencyId, UUID campaignId) {
        return updateCampaignStatus(agencyId, campaignId, "PUBLISHED", "ACTIVE", AuditAction.CAMPAIGN_RESUME);
    }

    @CacheEvict(value = "campaigns", allEntries = true)
    public Campaign publishCampaign(UUID agencyId, UUID campaignId) {
        Campaign c = getCampaign(agencyId, campaignId);
        if (!"DRAFT".equals(c.getStatus())) {
            throw new IllegalStateException("Only DRAFT campaigns can be published");
        }

        var result = executorService.publishCampaign(agencyId, campaignId);
        if (!"PUBLISHED".equals(result.get("status"))) {
            String error = String.valueOf(result.getOrDefault("error", "Failed to publish campaign in Meta"));
            throw new IllegalStateException(error);
        }
        return getCampaign(agencyId, campaignId);
    }

    private Campaign updateCampaignStatus(UUID agencyId, UUID campaignId,
                                          String localStatus, String metaStatus,
                                          AuditAction auditAction) {
        TenantContext ctx = TenantContextHolder.require();
        Campaign campaign = getCampaign(agencyId, campaignId);
        String before = campaign.getStatus();

        if (campaign.getMetaCampaignId() != null && !campaign.getMetaCampaignId().isBlank()) {
            MetaConnection conn = metaConnectionRepository.findByAgencyIdAndClientId(agencyId, campaign.getClientId())
                    .orElseThrow(() -> new IllegalStateException("No Meta connection for client " + campaign.getClientId()));
            try {
                String accessToken = metaService.getAccessToken(conn);
                log.info("Updating Meta campaign status: campaignId={}, metaCampaignId={}, status={}",
                        campaignId, campaign.getMetaCampaignId(), metaStatus);
                metaGraphApiClient.updateCampaignStatus(accessToken, campaign.getMetaCampaignId(), metaStatus);
            } catch (Exception e) {
                String error = "Failed to update campaign in Meta: " + e.getMessage();
                log.error("{} (campaignId={}, metaCampaignId={})", error, campaignId, campaign.getMetaCampaignId(), e);
                throw new IllegalStateException(error, e);
            }
        }

        campaign.setStatus(localStatus);
        Campaign saved = campaignRepository.save(campaign);

        auditService.log(agencyId, campaign.getClientId(), ctx.getUserId(), ctx.getRole(),
                auditAction, "Campaign", campaignId,
                before, saved.getStatus(), null);

        return saved;
    }

    // ──────── Adset ────────

    @Transactional(readOnly = true)
    public List<Adset> listAdsets(UUID campaignId) {
        return adsetRepository.findAllByCampaignId(campaignId);
    }

    public Adset createAdset(UUID agencyId, UUID campaignId, CreateAdsetRequest req) {
        TenantContext ctx = TenantContextHolder.require();
        log.info("Creating adset: name={}, campaignId={}, dailyBudget={}", req.name(), campaignId, req.dailyBudget());

        // Verify campaign belongs to agency
        Campaign campaign = getCampaign(agencyId, campaignId);

        Adset a = new Adset();
        a.setAgencyId(agencyId);
        a.setClientId(campaign.getClientId());
        a.setCampaignId(campaignId);
        a.setName(req.name());
        a.setDailyBudget(req.dailyBudget());
        a.setTargetingJson(req.targetingJson() != null ? req.targetingJson() : "{}");
        a.setOptimizationGoal(req.optimizationGoal() != null && !req.optimizationGoal().isBlank()
            ? req.optimizationGoal()
            : "CONVERSIONS");
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
    public List<Ad> listAds(UUID agencyId, UUID adsetId) {
        // Verify adset belongs to agency
        adsetRepository.findByIdAndAgencyId(adsetId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Adset", adsetId));
        return adRepository.findAllByAdsetId(adsetId);
    }

    public Ad createAd(UUID agencyId, UUID adsetId, CreateAdRequest req) {
        TenantContext ctx = TenantContextHolder.require();
        log.info("Creating ad: name={}, adsetId={}", req.name(), adsetId);

        // Verify adset belongs to agency
        Adset adset = adsetRepository.findByIdAndAgencyId(adsetId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Adset", adsetId));

        Ad ad = new Ad();
        ad.setAgencyId(agencyId);
        ad.setClientId(adset.getClientId());
        ad.setAdsetId(adsetId);
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

    // ──────── Manual full campaign creation ────────

    /**
     * Create a full campaign structure (campaign + adsets + ads) from a manual wizard request.
     * Returns a response with the full tree including all generated IDs.
     */
    @CacheEvict(value = "campaigns", key = "#agencyId + '_' + #clientId")
    public ManualCampaignResponse createFullCampaign(UUID agencyId, UUID clientId, ManualCampaignRequest req) {
        TenantContext ctx = TenantContextHolder.require();
        log.info("Creating full manual campaign: name={}, objective={}, clientId={}, adsets={}",
                req.name(), req.objective(), clientId, req.adsets().size());

        clientRepository.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        // Create campaign
        Campaign campaign = new Campaign();
        campaign.setAgencyId(agencyId);
        campaign.setClientId(clientId);
        campaign.setPlatform("META");
        campaign.setName(req.name());
        campaign.setObjective(req.objective());
        campaign.setStatus("DRAFT");
        campaign.setCreatedBy(ctx.getUserId());
        campaign.setCreatedAt(OffsetDateTime.now());
        campaign.setUpdatedAt(OffsetDateTime.now());
        campaign = campaignRepository.save(campaign);

        auditService.log(agencyId, clientId, ctx.getUserId(), ctx.getRole(),
                AuditAction.CAMPAIGN_CREATE, "Campaign", campaign.getId(),
                null, campaign, null);

        // Create adsets + ads
        List<ManualCampaignResponse.AdsetResult> adsetResults = new ArrayList<>();

        for (ManualCampaignRequest.AdsetPayload adsetPayload : req.adsets()) {
            Adset adset = new Adset();
            adset.setAgencyId(agencyId);
            adset.setClientId(clientId);
            adset.setCampaignId(campaign.getId());
            adset.setName(adsetPayload.name());
            adset.setDailyBudget(adsetPayload.dailyBudget());

            // Serialize targeting object to JSON string
            String targetingJson = "{}";
            if (adsetPayload.targeting() != null) {
                try {
                    targetingJson = objectMapper.writeValueAsString(adsetPayload.targeting());
                } catch (Exception e) {
                    log.warn("Failed to serialize targeting, using empty: {}", e.getMessage());
                }
            }
            adset.setTargetingJson(targetingJson);
            adset.setOptimizationGoal(adsetPayload.optimizationGoal() != null
                    ? adsetPayload.optimizationGoal() : "OFFSITE_CONVERSIONS");
            adset.setStatus("DRAFT");
            adset.setCreatedAt(OffsetDateTime.now());
            adset.setUpdatedAt(OffsetDateTime.now());
            adset = adsetRepository.save(adset);

            auditService.log(agencyId, clientId, ctx.getUserId(), ctx.getRole(),
                    AuditAction.ADSET_CREATE, "Adset", adset.getId(),
                    null, adset, null);

            // Create ads for this adset
            List<ManualCampaignResponse.AdResult> adResults = new ArrayList<>();
            for (ManualCampaignRequest.AdPayload adPayload : adsetPayload.ads()) {
                Ad ad = new Ad();
                ad.setAgencyId(agencyId);
                ad.setClientId(clientId);
                ad.setAdsetId(adset.getId());
                ad.setName(adPayload.name());
                ad.setCreativeAssetId(adPayload.creativeAssetId());
                ad.setCopyVariantId(adPayload.copyVariantId());
                ad.setPrimaryText(adPayload.primaryText());
                ad.setHeadline(adPayload.headline());
                ad.setDescription(adPayload.description());
                ad.setCta(adPayload.ctaType());
                ad.setDestinationUrl(adPayload.destinationUrl());
                ad.setStatus("DRAFT");
                ad.setCreatedAt(OffsetDateTime.now());
                ad.setUpdatedAt(OffsetDateTime.now());
                ad = adRepository.save(ad);

                auditService.log(agencyId, clientId, ctx.getUserId(), ctx.getRole(),
                        AuditAction.AD_CREATE, "Ad", ad.getId(),
                        null, ad, null);

                adResults.add(new ManualCampaignResponse.AdResult(
                        ad.getId(), ad.getName(), ad.getCreativeAssetId(),
                        ad.getCopyVariantId(), ad.getPrimaryText(), ad.getHeadline(),
                        ad.getDescription(), ad.getCta(), ad.getDestinationUrl(), ad.getStatus()
                ));
            }

            adsetResults.add(new ManualCampaignResponse.AdsetResult(
                    adset.getId(), adset.getName(), adset.getDailyBudget(),
                    adset.getOptimizationGoal(), adset.getTargetingJson(), adset.getStatus(),
                    adResults
            ));
        }

        return new ManualCampaignResponse(
                campaign.getId(), campaign.getName(), campaign.getObjective(),
                campaign.getPlatform(), campaign.getStatus(), adsetResults
        );
    }
}
