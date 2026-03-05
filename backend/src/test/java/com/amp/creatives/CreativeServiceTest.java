package com.amp.creatives;

import com.amp.audit.AuditService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreativeServiceTest {

    private static final UUID AGENCY_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID USER_ID    = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PACKAGE_ID = UUID.randomUUID();

    @Mock private CreativeAssetRepository assetRepository;
    @Mock private CreativeAnalysisRepository analysisRepository;
    @Mock private CreativePackageRepository packageRepository;
    @Mock private CopyVariantRepository copyVariantRepository;
    @Mock private AuditService auditService;
    @Mock private S3StorageService s3StorageService;
    @Mock private S3Properties s3Properties;

    @InjectMocks
    private CreativeService creativeService;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.set(new TenantContext(AGENCY_ID, USER_ID, "test@local", "AGENCY_ADMIN"));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // ──────── helpers ────────

    private CreativeAsset buildAsset(String filename) {
        CreativeAsset a = new CreativeAsset();
        a.setId(UUID.randomUUID());
        a.setAgencyId(AGENCY_ID);
        a.setClientId(CLIENT_ID);
        a.setAssetType("IMAGE");
        a.setS3Bucket("test-bucket");
        a.setS3Key("key/" + filename);
        a.setOriginalFilename(filename);
        a.setMimeType("image/jpeg");
        a.setSizeBytes(100_000L);
        a.setChecksumSha256("sha256_test");
        a.setStatus("READY");
        a.setCreatedBy(USER_ID);
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());
        return a;
    }

    private CreativePackage buildPackage(String status) {
        CreativePackage p = new CreativePackage();
        p.setId(PACKAGE_ID);
        p.setAgencyId(AGENCY_ID);
        p.setClientId(CLIENT_ID);
        p.setName("Test Package");
        p.setObjective("SALES");
        p.setStatus(status);
        p.setCreatedBy(USER_ID);
        p.setCreatedAt(OffsetDateTime.now());
        return p;
    }

    // ──────── listAssets ────────

    @Test
    @DisplayName("listAssets — returns all assets for client")
    void getAssets_byClientId() {
        when(assetRepository.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(
                        buildAsset("img1.jpg"), buildAsset("img2.jpg"),
                        buildAsset("img3.jpg"), buildAsset("img4.jpg")
                ));

        List<AssetResponse> result = creativeService.listAssets(AGENCY_ID, CLIENT_ID);

        assertThat(result).hasSize(4);
    }

    // ──────── createPackage ────────

    @Test
    @DisplayName("createPackage — success: status DRAFT, save called")
    void createPackage_success() {
        CreatePackageRequest request = new CreatePackageRequest(CLIENT_ID, "Test Package", "SALES");

        when(packageRepository.save(any(CreativePackage.class))).thenAnswer(inv -> {
            CreativePackage p = inv.getArgument(0);
            p.setId(PACKAGE_ID);
            return p;
        });

        PackageResponse result = creativeService.createPackage(AGENCY_ID, CLIENT_ID, request);

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.name()).isEqualTo("Test Package");
        verify(packageRepository).save(any(CreativePackage.class));
    }

    // ──────── submitPackage ────────

    @Test
    @DisplayName("submitPackage — success: DRAFT → IN_REVIEW")
    void submitPackage_success() {
        CreativePackage pkg = buildPackage("DRAFT");
        when(packageRepository.findByIdAndAgencyId(PACKAGE_ID, AGENCY_ID)).thenReturn(Optional.of(pkg));
        when(packageRepository.save(any(CreativePackage.class))).thenAnswer(inv -> inv.getArgument(0));

        PackageResponse result = creativeService.submitPackage(AGENCY_ID, PACKAGE_ID);

        assertThat(result.status()).isEqualTo("IN_REVIEW");
    }

    @Test
    @DisplayName("submitPackage — not DRAFT: throws IllegalStateException")
    void submitPackage_notDraft() {
        CreativePackage pkg = buildPackage("APPROVED");
        when(packageRepository.findByIdAndAgencyId(PACKAGE_ID, AGENCY_ID)).thenReturn(Optional.of(pkg));

        assertThatThrownBy(() -> creativeService.submitPackage(AGENCY_ID, PACKAGE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    // ──────── approvePackage ────────

    @Test
    @DisplayName("approvePackage — success: IN_REVIEW → APPROVED, approvedBy set, approvedAt not null")
    void approvePackage_success() {
        CreativePackage pkg = buildPackage("IN_REVIEW");
        when(packageRepository.findByIdAndAgencyId(PACKAGE_ID, AGENCY_ID)).thenReturn(Optional.of(pkg));
        when(packageRepository.save(any(CreativePackage.class))).thenAnswer(inv -> inv.getArgument(0));

        PackageResponse result = creativeService.approvePackage(AGENCY_ID, PACKAGE_ID);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.approvedBy()).isEqualTo(USER_ID);
        assertThat(result.approvedAt()).isNotNull();
    }

    @Test
    @DisplayName("approvePackage — not IN_REVIEW: throws IllegalStateException")
    void approvePackage_notInReview() {
        CreativePackage pkg = buildPackage("DRAFT");
        when(packageRepository.findByIdAndAgencyId(PACKAGE_ID, AGENCY_ID)).thenReturn(Optional.of(pkg));

        assertThatThrownBy(() -> creativeService.approvePackage(AGENCY_ID, PACKAGE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_REVIEW");
    }

    // ──────── not found ────────

    @Test
    @DisplayName("submitPackage — not found: throws ResourceNotFoundException")
    void submitPackage_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(packageRepository.findByIdAndAgencyId(unknownId, AGENCY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creativeService.submitPackage(AGENCY_ID, unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
