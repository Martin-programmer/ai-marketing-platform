package com.amp.creatives;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for creative assets, packages and their lifecycle.
 */
@Service
@Transactional
public class CreativeService {

    private final CreativeAssetRepository assetRepository;
    private final CreativeAnalysisRepository analysisRepository;
    private final CreativePackageRepository packageRepository;
    private final AuditService auditService;

    public CreativeService(CreativeAssetRepository assetRepository,
                           CreativeAnalysisRepository analysisRepository,
                           CreativePackageRepository packageRepository,
                           AuditService auditService) {
        this.assetRepository = assetRepository;
        this.analysisRepository = analysisRepository;
        this.packageRepository = packageRepository;
        this.auditService = auditService;
    }

    // ---- Assets ----

    @Transactional(readOnly = true)
    public List<AssetResponse> listAssets(UUID agencyId, UUID clientId) {
        return assetRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream()
                .map(AssetResponse::from)
                .toList();
    }

    public AssetResponse createAsset(UUID agencyId, CreateAssetRequest request) {
        UUID userId = TenantContextHolder.require().getUserId();
        OffsetDateTime now = OffsetDateTime.now();

        CreativeAsset asset = new CreativeAsset();
        asset.setAgencyId(agencyId);
        asset.setClientId(request.clientId());
        asset.setAssetType(request.assetType());
        asset.setOriginalFilename(request.originalFilename());
        asset.setMimeType(request.mimeType() != null ? request.mimeType() : "application/octet-stream");
        asset.setSizeBytes(request.sizeBytes() != null ? request.sizeBytes() : 0L);
        asset.setS3Bucket("");
        asset.setS3Key("");
        asset.setChecksumSha256("");
        asset.setStatus("UPLOADING");
        asset.setCreatedBy(userId);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);

        CreativeAsset saved = assetRepository.save(asset);

        auditService.log(agencyId, request.clientId(), null, null,
                AuditAction.CREATIVE_UPLOAD, "CREATIVE_ASSET", saved.getId(),
                null, saved, UUID.randomUUID().toString());

        return AssetResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AssetResponse getAsset(UUID agencyId, UUID assetId) {
        CreativeAsset asset = assetRepository.findByIdAndAgencyId(assetId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativeAsset", assetId));
        return AssetResponse.from(asset);
    }

    @Transactional(readOnly = true)
    public CreativeAnalysis getAnalysis(UUID assetId) {
        return analysisRepository.findByCreativeAssetId(assetId).orElse(null);
    }

    // ---- Packages ----

    @Transactional(readOnly = true)
    public List<PackageResponse> listPackages(UUID agencyId, UUID clientId) {
        return packageRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream()
                .map(PackageResponse::from)
                .toList();
    }

    public PackageResponse createPackage(UUID agencyId, CreatePackageRequest request) {
        UUID userId = TenantContextHolder.require().getUserId();
        OffsetDateTime now = OffsetDateTime.now();

        CreativePackage pkg = new CreativePackage();
        pkg.setAgencyId(agencyId);
        pkg.setClientId(request.clientId());
        pkg.setName(request.name());
        pkg.setObjective(request.objective() != null ? request.objective() : "SALES");
        pkg.setStatus("DRAFT");
        pkg.setCreatedBy(userId);
        pkg.setCreatedAt(now);

        CreativePackage saved = packageRepository.save(pkg);

        auditService.log(agencyId, request.clientId(), null, null,
                AuditAction.CREATIVE_UPLOAD, "CREATIVE_PACKAGE", saved.getId(),
                null, saved, UUID.randomUUID().toString());

        return PackageResponse.from(saved);
    }

    public PackageResponse submitPackage(UUID agencyId, UUID packageId) {
        CreativePackage pkg = packageRepository.findByIdAndAgencyId(packageId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativePackage", packageId));

        if (!"DRAFT".equals(pkg.getStatus())) {
            throw new IllegalStateException(
                    "Package must be in DRAFT status to submit, current: " + pkg.getStatus());
        }

        String before = pkg.getStatus();
        pkg.setStatus("IN_REVIEW");
        CreativePackage saved = packageRepository.save(pkg);

        auditService.log(agencyId, pkg.getClientId(), null, null,
                AuditAction.CREATIVE_APPROVE, "CREATIVE_PACKAGE", packageId,
                before, "IN_REVIEW", UUID.randomUUID().toString());

        return PackageResponse.from(saved);
    }

    public PackageResponse approvePackage(UUID agencyId, UUID packageId) {
        CreativePackage pkg = packageRepository.findByIdAndAgencyId(packageId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativePackage", packageId));

        if (!"IN_REVIEW".equals(pkg.getStatus())) {
            throw new IllegalStateException(
                    "Package must be in IN_REVIEW status to approve, current: " + pkg.getStatus());
        }

        String before = pkg.getStatus();
        UUID userId = TenantContextHolder.require().getUserId();
        pkg.setStatus("APPROVED");
        pkg.setApprovedBy(userId);
        pkg.setApprovedAt(OffsetDateTime.now());
        CreativePackage saved = packageRepository.save(pkg);

        auditService.log(agencyId, pkg.getClientId(), null, null,
                AuditAction.CREATIVE_APPROVE, "CREATIVE_PACKAGE", packageId,
                before, "APPROVED", UUID.randomUUID().toString());

        return PackageResponse.from(saved);
    }
}
