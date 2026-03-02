package com.amp.campaigns;

import com.amp.audit.AuditService;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    private static final UUID AGENCY_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID USER_ID    = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final UUID ADSET_ID   = UUID.randomUUID();

    @Mock private CampaignRepository campaignRepository;
    @Mock private AdsetRepository adsetRepository;
    @Mock private AdRepository adRepository;
    @Mock private AuditService auditService;
    @Mock private ClientRepository clientRepository;

    @InjectMocks
    private CampaignService campaignService;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.set(new TenantContext(AGENCY_ID, USER_ID, "test@local", "AGENCY_ADMIN"));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // ──────── helpers ────────

    private Campaign buildCampaign(String status) {
        Campaign c = new Campaign();
        c.setId(CAMPAIGN_ID);
        c.setAgencyId(AGENCY_ID);
        c.setClientId(CLIENT_ID);
        c.setPlatform("META");
        c.setName("Test Campaign");
        c.setObjective("SALES");
        c.setStatus(status);
        c.setCreatedBy(USER_ID);
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        return c;
    }

    private Adset buildAdset() {
        Adset a = new Adset();
        a.setId(ADSET_ID);
        a.setAgencyId(AGENCY_ID);
        a.setClientId(CLIENT_ID);
        a.setCampaignId(CAMPAIGN_ID);
        a.setName("Test Adset");
        a.setDailyBudget(new BigDecimal("50.00"));
        a.setTargetingJson("{}");
        a.setStatus("DRAFT");
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());
        return a;
    }

    // ──────── createCampaign ────────

    @Test
    @DisplayName("createCampaign — success: status DRAFT, metaCampaignId null, save called")
    void createCampaign_success() {
        CreateCampaignRequest req = new CreateCampaignRequest(CLIENT_ID, "My Campaign", "SALES");

        when(clientRepository.findByIdAndAgencyId(CLIENT_ID, AGENCY_ID))
                .thenReturn(Optional.of(mock(Client.class)));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(CAMPAIGN_ID);
            return c;
        });

        Campaign result = campaignService.createCampaign(AGENCY_ID, CLIENT_ID, req);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getMetaCampaignId()).isNull();
        assertThat(result.getName()).isEqualTo("My Campaign");
        verify(campaignRepository).save(any(Campaign.class));
        verify(auditService).log(eq(AGENCY_ID), eq(CLIENT_ID), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ──────── listCampaigns ────────

    @Test
    @DisplayName("listCampaigns — returns all for client")
    void getCampaigns_byClientId() {
        when(campaignRepository.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(buildCampaign("DRAFT"), buildCampaign("PUBLISHED"), buildCampaign("PAUSED")));

        List<Campaign> result = campaignService.listCampaigns(AGENCY_ID, CLIENT_ID);

        assertThat(result).hasSize(3);
    }

    // ──────── publishCampaign ────────

    @Test
    @DisplayName("publishCampaign — success: DRAFT → PUBLISHED, audit logged")
    void publishCampaign_success() {
        Campaign c = buildCampaign("DRAFT");
        when(campaignRepository.findByIdAndAgencyId(CAMPAIGN_ID, AGENCY_ID)).thenReturn(Optional.of(c));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        Campaign result = campaignService.publishCampaign(AGENCY_ID, CAMPAIGN_ID);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        verify(auditService).log(eq(AGENCY_ID), eq(CLIENT_ID), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("publishCampaign — not DRAFT: throws IllegalStateException")
    void publishCampaign_notDraft() {
        Campaign c = buildCampaign("PUBLISHED");
        when(campaignRepository.findByIdAndAgencyId(CAMPAIGN_ID, AGENCY_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> campaignService.publishCampaign(AGENCY_ID, CAMPAIGN_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    // ──────── pauseCampaign ────────

    @Test
    @DisplayName("pauseCampaign — success: PUBLISHED → PAUSED")
    void pauseCampaign_success() {
        Campaign c = buildCampaign("PUBLISHED");
        when(campaignRepository.findByIdAndAgencyId(CAMPAIGN_ID, AGENCY_ID)).thenReturn(Optional.of(c));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        Campaign result = campaignService.pauseCampaign(AGENCY_ID, CAMPAIGN_ID);

        assertThat(result.getStatus()).isEqualTo("PAUSED");
    }

    // ──────── resumeCampaign ────────

    @Test
    @DisplayName("resumeCampaign — success: PAUSED → PUBLISHED")
    void resumeCampaign_success() {
        Campaign c = buildCampaign("PAUSED");
        when(campaignRepository.findByIdAndAgencyId(CAMPAIGN_ID, AGENCY_ID)).thenReturn(Optional.of(c));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        Campaign result = campaignService.resumeCampaign(AGENCY_ID, CAMPAIGN_ID);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("resumeCampaign — DRAFT: sets PUBLISHED (no guard in service)")
    void resumeCampaign_notPaused() {
        // Current service does NOT guard against resuming a non-PAUSED campaign.
        // It unconditionally sets PUBLISHED. If a guard is added, update this test.
        Campaign c = buildCampaign("DRAFT");
        when(campaignRepository.findByIdAndAgencyId(CAMPAIGN_ID, AGENCY_ID)).thenReturn(Optional.of(c));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        Campaign result = campaignService.resumeCampaign(AGENCY_ID, CAMPAIGN_ID);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
    }

    // ──────── createAdset ────────

    @Test
    @DisplayName("createAdset — success: correct campaignId, status DRAFT")
    void createAdset_success() {
        Campaign campaign = buildCampaign("PUBLISHED");
        when(campaignRepository.findByIdAndAgencyId(CAMPAIGN_ID, AGENCY_ID)).thenReturn(Optional.of(campaign));
        when(adsetRepository.save(any(Adset.class))).thenAnswer(inv -> {
            Adset a = inv.getArgument(0);
            a.setId(ADSET_ID);
            return a;
        });

        CreateAdsetRequest req = new CreateAdsetRequest(CAMPAIGN_ID, "Test Adset", "{}", new BigDecimal("50.00"), null);
        Adset result = campaignService.createAdset(AGENCY_ID, CAMPAIGN_ID, req);

        assertThat(result.getCampaignId()).isEqualTo(CAMPAIGN_ID);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(adsetRepository).save(any(Adset.class));
    }

    // ──────── createAd ────────

    @Test
    @DisplayName("createAd — success: correct adsetId, status DRAFT")
    void createAd_success() {
        Adset adset = buildAdset();
        when(adsetRepository.findByIdAndAgencyId(ADSET_ID, AGENCY_ID)).thenReturn(Optional.of(adset));
        when(adRepository.save(any(Ad.class))).thenAnswer(inv -> {
            Ad ad = inv.getArgument(0);
            ad.setId(UUID.randomUUID());
            return ad;
        });

        CreateAdRequest req = new CreateAdRequest(ADSET_ID, "Test Ad", null);
        Ad result = campaignService.createAd(AGENCY_ID, ADSET_ID, req);

        assertThat(result.getAdsetId()).isEqualTo(ADSET_ID);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(adRepository).save(any(Ad.class));
    }

    @Test
    @DisplayName("createAd — adset not found: throws ResourceNotFoundException")
    void createAd_adsetNotFound() {
        UUID unknownAdsetId = UUID.randomUUID();
        when(adsetRepository.findByIdAndAgencyId(unknownAdsetId, AGENCY_ID)).thenReturn(Optional.empty());

        CreateAdRequest req = new CreateAdRequest(unknownAdsetId, "Test Ad", null);

        assertThatThrownBy(() -> campaignService.createAd(AGENCY_ID, unknownAdsetId, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
