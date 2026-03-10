package com.amp.creatives;

import com.amp.ai.AiProperties;
import com.amp.ai.CopyFactoryService;
import com.amp.ai.CreativeAnalyzerService;
import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Business logic for creative assets, packages and their lifecycle.
 */
@Service
@Transactional
public class CreativeService {

    private static final Logger log = LoggerFactory.getLogger(CreativeService.class);

    private final CreativeAssetRepository assetRepository;
    private final CreativeAnalysisRepository analysisRepository;
    private final CreativePackageRepository packageRepository;
    private final CopyVariantRepository copyVariantRepository;
    private final AuditService auditService;
    private final S3StorageService s3StorageService;
    private final S3Properties s3Properties;
    private final CreativeAnalyzerService creativeAnalyzerService;
    private final CopyFactoryService copyFactoryService;
    private final AiProperties aiProperties;

    public CreativeService(CreativeAssetRepository assetRepository,
                           CreativeAnalysisRepository analysisRepository,
                           CreativePackageRepository packageRepository,
                           CopyVariantRepository copyVariantRepository,
                           AuditService auditService,
                           S3StorageService s3StorageService,
                           S3Properties s3Properties,
                           CreativeAnalyzerService creativeAnalyzerService,
                           CopyFactoryService copyFactoryService,
                           AiProperties aiProperties) {
        this.assetRepository = assetRepository;
        this.analysisRepository = analysisRepository;
        this.packageRepository = packageRepository;
        this.copyVariantRepository = copyVariantRepository;
        this.auditService = auditService;
        this.s3StorageService = s3StorageService;
        this.s3Properties = s3Properties;
        this.creativeAnalyzerService = creativeAnalyzerService;
        this.copyFactoryService = copyFactoryService;
        this.aiProperties = aiProperties;
    }

    // ---- Assets ----

    @Transactional(readOnly = true)
    @Cacheable(value = "creativeAssets", key = "#agencyId + '_' + #clientId")
    public List<AssetResponse> listAssets(UUID agencyId, UUID clientId) {
        return assetRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream()
                .map(AssetResponse::from)
                .toList();
    }

    @CacheEvict(value = "creativeAssets", key = "#agencyId + '_' + #request.clientId()")
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

        auditService.log(agencyId, request.clientId(), null, TenantContextHolder.require().getRole(),
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

    // ---- S3 Upload Flow ----

    /**
     * Initiate an upload: create UPLOADING record and return presigned PUT URL.
     */
    @CacheEvict(value = "creativeAssets", key = "#agencyId + '_' + #clientId")
    public UploadInitResponse initiateUpload(UUID agencyId, UUID clientId, UploadInitRequest request) {
        UUID userId = TenantContextHolder.require().getUserId();
        OffsetDateTime now = OffsetDateTime.now();

        CreativeAsset asset = new CreativeAsset();
        asset.setAgencyId(agencyId);
        asset.setClientId(clientId);
        asset.setAssetType(request.assetType() != null ? request.assetType() : detectAssetType(request.mimeType()));
        asset.setOriginalFilename(request.fileName());
        asset.setMimeType(request.mimeType());
        asset.setSizeBytes(request.sizeBytes());
        asset.setChecksumSha256(request.checksumSha256() != null ? request.checksumSha256() : "pending");
        asset.setS3Bucket("");
        asset.setS3Key("");
        asset.setStatus("UPLOADING");
        asset.setCreatedBy(userId);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);

        // Save first to get ID, then generate S3 key
        CreativeAsset saved = assetRepository.save(asset);
        String s3Key = s3StorageService.generateS3Key(agencyId, clientId, saved.getId(), request.fileName());
        saved.setS3Bucket(s3Properties.getBucket());
        saved.setS3Key(s3Key);
        saved = assetRepository.save(saved);

        // Generate presigned PUT URL
        String presignedPutUrl = s3StorageService.generatePresignedPutUrl(s3Key, request.mimeType(), request.sizeBytes());

        auditService.log(agencyId, clientId, userId, TenantContextHolder.require().getRole(),
                AuditAction.CREATIVE_UPLOAD, "CREATIVE_ASSET", saved.getId(),
                null, saved.getStatus(), UUID.randomUUID().toString());

        log.info("Initiated upload for asset {} ({}), s3Key={}", saved.getId(), request.fileName(), s3Key);
        return new UploadInitResponse(saved.getId(), s3Key, presignedPutUrl, s3Properties.getBucket());
    }

    /**
     * Complete an upload: validate file in S3 and mark as READY.
     */
    @CacheEvict(value = "creativeAssets", allEntries = true)
    public AssetResponse completeUpload(UUID agencyId, UUID assetId, UploadCompleteRequest request) {
        CreativeAsset asset = assetRepository.findByIdAndAgencyId(assetId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativeAsset", assetId));

        if (!"UPLOADING".equals(asset.getStatus())) {
            throw new IllegalStateException("Asset is not in UPLOADING status: " + asset.getStatus());
        }

        // Verify file exists in S3
        if (!s3StorageService.objectExists(asset.getS3Key())) {
            throw new IllegalStateException("File not found in S3. Upload may not have completed.");
        }

        // Update checksum if provided
        if (request != null && request.checksumSha256() != null) {
            asset.setChecksumSha256(request.checksumSha256());
        }

        // Update dimensions if provided
        if (request != null) {
            if (request.widthPx() != null) asset.setWidthPx(request.widthPx());
            if (request.heightPx() != null) asset.setHeightPx(request.heightPx());
        }

        asset.setStatus("READY");
        asset.setUpdatedAt(OffsetDateTime.now());
        CreativeAsset saved = assetRepository.save(asset);

        log.info("Upload complete for asset {}, status=READY", assetId);

        // Trigger async AI analysis if enabled
        if (aiProperties.getAnalyzer().isEnabled() && aiProperties.getAnalyzer().isAutoAnalyzeOnUpload()) {
            final CreativeAsset assetCopy = saved;
            final UUID userId = TenantContextHolder.require().getUserId();
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Starting async creative analysis for asset {}", assetId);
                    CreativeAnalysis analysis = creativeAnalyzerService.analyze(assetCopy);
                    if (analysis != null && aiProperties.getAnalyzer().isAutoGenerateCopy()) {
                        log.info("Auto-generating copy variants for asset {}", assetId);
                        copyFactoryService.generateCopy(analysis, userId);
                    }
                } catch (Exception e) {
                    log.error("Async creative analysis failed for asset {}: {}", assetId, e.getMessage(), e);
                }
            });
        }

        return AssetResponse.from(saved);
    }

    /**
     * Get a presigned GET URL for viewing/downloading an asset.
     */
    @Transactional(readOnly = true)
    public String getPresignedViewUrl(UUID agencyId, UUID assetId) {
        CreativeAsset asset = assetRepository.findByIdAndAgencyId(assetId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativeAsset", assetId));
        return s3StorageService.generatePresignedGetUrl(asset.getS3Key());
    }

    /**
     * Delete an asset and its S3 object.
     */
    @CacheEvict(value = "creativeAssets", allEntries = true)
    public void deleteAsset(UUID agencyId, UUID assetId) {
        CreativeAsset asset = assetRepository.findByIdAndAgencyId(assetId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativeAsset", assetId));
        s3StorageService.deleteObject(asset.getS3Key());
        assetRepository.delete(asset);
        log.info("Deleted asset {} and S3 object {}", assetId, asset.getS3Key());
    }

    private String detectAssetType(String mimeType) {
        if (mimeType == null) return "IMAGE";
        if (mimeType.startsWith("video/")) return "VIDEO";
        if (mimeType.startsWith("image/")) return "IMAGE";
        return "DOC";
    }

    // ---- Packages ----

    @Transactional(readOnly = true)
    @Cacheable(value = "creativePackages", key = "#agencyId + '_' + #clientId")
    public List<PackageResponse> listPackages(UUID agencyId, UUID clientId) {
        return packageRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream()
                .map(PackageResponse::from)
                .toList();
    }

    @CacheEvict(value = "creativePackages", key = "#agencyId + '_' + #clientId")
    public PackageResponse createPackage(UUID agencyId, UUID clientId, CreatePackageRequest request) {
        log.info("Creating creative package: name={}, clientId={}", request.name(), clientId);
        UUID userId = TenantContextHolder.require().getUserId();
        OffsetDateTime now = OffsetDateTime.now();

        CreativePackage pkg = new CreativePackage();
        pkg.setAgencyId(agencyId);
        pkg.setClientId(clientId);
        pkg.setName(request.name());
        pkg.setObjective(request.objective() != null ? request.objective() : "SALES");
        pkg.setStatus("DRAFT");
        pkg.setCreatedBy(userId);
        pkg.setCreatedAt(now);

        CreativePackage saved = packageRepository.save(pkg);

        auditService.log(agencyId, clientId, null, TenantContextHolder.require().getRole(),
                AuditAction.CREATIVE_UPLOAD, "CREATIVE_PACKAGE", saved.getId(),
                null, saved, UUID.randomUUID().toString());

        return PackageResponse.from(saved);
    }

    @CacheEvict(value = "creativePackages", allEntries = true)
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

        auditService.log(agencyId, pkg.getClientId(), null, TenantContextHolder.require().getRole(),
                AuditAction.CREATIVE_APPROVE, "CREATIVE_PACKAGE", packageId,
                before, "IN_REVIEW", UUID.randomUUID().toString());

        return PackageResponse.from(saved);
    }

    @CacheEvict(value = "creativePackages", allEntries = true)
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

        auditService.log(agencyId, pkg.getClientId(), null, TenantContextHolder.require().getRole(),
                AuditAction.CREATIVE_APPROVE, "CREATIVE_PACKAGE", packageId,
                before, "APPROVED", UUID.randomUUID().toString());

        return PackageResponse.from(saved);
    }

    // ---- Copy Variants ----

    @Transactional(readOnly = true)
    public List<CopyVariantResponse> listCopyVariants(UUID agencyId, UUID clientId) {
        return copyVariantRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream()
                .map(CopyVariantResponse::from)
                .toList();
    }

    public CopyVariantResponse createCopyVariant(UUID agencyId, UUID clientId,
                                                  CreateCopyVariantRequest request) {
        log.info("Creating copy variant: headline={}, clientId={}", request.headline(), clientId);
        UUID userId = TenantContextHolder.require().getUserId();
        OffsetDateTime now = OffsetDateTime.now();

        CopyVariant cv = new CopyVariant();
        cv.setAgencyId(agencyId);
        cv.setClientId(clientId);
        cv.setLanguage(request.language());
        cv.setPrimaryText(request.primaryText());
        cv.setHeadline(request.headline());
        cv.setDescription(request.description());
        cv.setCta(request.cta());
        cv.setStatus("DRAFT");
        cv.setCreatedBy(userId);
        cv.setCreatedAt(now);
        cv.setUpdatedAt(now);

        CopyVariant saved = copyVariantRepository.save(cv);

        auditService.log(agencyId, clientId, null, TenantContextHolder.require().getRole(),
                AuditAction.CREATIVE_UPLOAD, "COPY_VARIANT", saved.getId(),
                null, saved, UUID.randomUUID().toString());

        return CopyVariantResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CopyVariantResponse getCopyVariant(UUID agencyId, UUID variantId) {
        CopyVariant cv = copyVariantRepository.findById(variantId)
                .filter(v -> v.getAgencyId().equals(agencyId))
                .orElseThrow(() -> new ResourceNotFoundException("CopyVariant", variantId));
        return CopyVariantResponse.from(cv);
    }

    @Transactional(readOnly = true)
    public List<CopyVariantResponse> listCopyVariantsForAsset(UUID assetId) {
        return copyVariantRepository.findByCreativeAssetId(assetId)
                .stream()
                .map(CopyVariantResponse::from)
                .toList();
    }

    // ──────── Client-ID resolvers (for permission checks) ────────

    @Transactional(readOnly = true)
    public UUID resolveAssetClientId(UUID agencyId, UUID assetId) {
        return assetRepository.findByIdAndAgencyId(assetId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativeAsset", assetId))
                .getClientId();
    }

    @Transactional(readOnly = true)
    public UUID resolvePackageClientId(UUID agencyId, UUID packageId) {
        return packageRepository.findByIdAndAgencyId(packageId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativePackage", packageId))
                .getClientId();
    }

    @Transactional(readOnly = true)
    public UUID resolveCopyVariantClientId(UUID agencyId, UUID variantId) {
        return copyVariantRepository.findById(variantId)
                .filter(v -> v.getAgencyId().equals(agencyId))
                .orElseThrow(() -> new ResourceNotFoundException("CopyVariant", variantId))
                .getClientId();
    }
}
