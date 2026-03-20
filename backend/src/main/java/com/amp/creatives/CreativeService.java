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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    private final CreativePackageItemRepository packageItemRepository;
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
                           CreativePackageItemRepository packageItemRepository,
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
        this.packageItemRepository = packageItemRepository;
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
    public List<AssetResponse> listAssets(UUID agencyId, UUID clientId) {
        log.info("Fetching creative assets for agency={}, client={}", agencyId, clientId);
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
    public List<PackageResponse> listPackages(UUID agencyId, UUID clientId) {
        log.info("Fetching creative packages for agency={}, client={}", agencyId, clientId);
        try {
            List<CreativePackage> packages = packageRepository.findAllByAgencyIdAndClientId(agencyId, clientId);
            log.info("Found {} creative packages for agency={}, client={}", packages.size(), agencyId, clientId);
            return packages.stream()
                    .map(PackageResponse::from)
                    .toList();
        } catch (Exception e) {
            log.error("Error fetching creative packages for agency={}, client={}: {}",
                    agencyId, clientId, e.getMessage(), e);
            throw e;
        }
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
        pkg.setNotes(request.notes());
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
    public PackageResponse updatePackage(UUID agencyId, UUID packageId, UpdatePackageRequest request) {
        CreativePackage pkg = requireEditablePackage(agencyId, packageId);
        pkg.setName(request.name());
        pkg.setObjective(request.objective() != null && !request.objective().isBlank() ? request.objective() : pkg.getObjective());
        pkg.setNotes(request.notes());
        return PackageResponse.from(packageRepository.save(pkg));
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

    @CacheEvict(value = "creativePackages", allEntries = true)
    public PackageResponse rejectPackage(UUID agencyId, UUID packageId) {
        CreativePackage pkg = packageRepository.findByIdAndAgencyId(packageId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativePackage", packageId));

        if (!"IN_REVIEW".equals(pkg.getStatus())) {
            throw new IllegalStateException(
                    "Package must be in IN_REVIEW status to reject, current: " + pkg.getStatus());
        }

        String before = pkg.getStatus();
        pkg.setStatus("DRAFT");
        pkg.setApprovedBy(null);
        pkg.setApprovedAt(null);
        CreativePackage saved = packageRepository.save(pkg);

        auditService.log(agencyId, pkg.getClientId(), null, TenantContextHolder.require().getRole(),
                AuditAction.CREATIVE_APPROVE, "CREATIVE_PACKAGE", packageId,
                before, "DRAFT", UUID.randomUUID().toString());

        return PackageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PackageItemResponse> listPackageItems(UUID agencyId, UUID packageId) {
        CreativePackage pkg = packageRepository.findByIdAndAgencyId(packageId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativePackage", packageId));
        return toPackageItemResponses(pkg, packageItemRepository.findAllByPackageIdOrderByCreatedAtAsc(packageId));
    }

    @CacheEvict(value = "creativePackages", allEntries = true)
    public PackageItemResponse addPackageItem(UUID agencyId, UUID packageId, CreatePackageItemRequest request) {
        CreativePackage pkg = requireEditablePackage(agencyId, packageId);

        CreativeAsset asset = assetRepository.findByIdAndAgencyId(request.creativeAssetId(), agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativeAsset", request.creativeAssetId()));
        CopyVariant copyVariant = copyVariantRepository.findById(request.copyVariantId())
                .filter(variant -> variant.getAgencyId().equals(agencyId))
                .orElseThrow(() -> new ResourceNotFoundException("CopyVariant", request.copyVariantId()));

        if (!pkg.getClientId().equals(asset.getClientId()) || !pkg.getClientId().equals(copyVariant.getClientId())) {
            throw new IllegalStateException("Package item must reference creatives and copy from the same client.");
        }
        if (copyVariant.getCreativeAssetId() != null && !copyVariant.getCreativeAssetId().equals(asset.getId())) {
            throw new IllegalStateException("Selected copy variant belongs to a different creative asset.");
        }

        CreativePackageItem item = new CreativePackageItem();
        item.setAgencyId(agencyId);
        item.setClientId(pkg.getClientId());
        item.setPackageId(pkg.getId());
        item.setCreativeAssetId(asset.getId());
        item.setCopyVariantId(copyVariant.getId());
        item.setCtaType(firstNonBlank(request.ctaType()));
        item.setDestinationUrl(firstNonBlank(request.destinationUrl()));
        item.setWeight(normalizeWeight(request.weight()));
        item.setCreatedAt(OffsetDateTime.now());

        CreativePackageItem saved = packageItemRepository.save(item);
        return toPackageItemResponse(saved, asset, copyVariant, analysisRepository.findByCreativeAssetId(asset.getId()).orElse(null));
    }

    @CacheEvict(value = "creativePackages", allEntries = true)
    public void deletePackageItem(UUID agencyId, UUID packageId, UUID itemId) {
        requireEditablePackage(agencyId, packageId);
        CreativePackageItem item = packageItemRepository.findByIdAndPackageId(itemId, packageId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativePackageItem", itemId));
        packageItemRepository.delete(item);
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
        cv.setCreativeAssetId(request.assetId());
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

    public CopyVariantResponse approveCopyVariant(UUID agencyId, UUID variantId) {
        return updateCopyVariantStatus(agencyId, variantId, "APPROVED", AuditAction.CREATIVE_APPROVE);
    }

    public CopyVariantResponse rejectCopyVariant(UUID agencyId, UUID variantId) {
        return updateCopyVariantStatus(agencyId, variantId, "REJECTED", AuditAction.CREATIVE_APPROVE);
    }

    @Transactional(readOnly = true)
    public List<CopyVariantResponse> listCopyVariantsForAsset(UUID assetId) {
        return copyVariantRepository.findByCreativeAssetId(assetId)
                .stream()
                .map(CopyVariantResponse::from)
                .toList();
    }

    private CopyVariantResponse updateCopyVariantStatus(UUID agencyId, UUID variantId,
                                                        String nextStatus, AuditAction auditAction) {
        CopyVariant variant = copyVariantRepository.findById(variantId)
                .filter(v -> v.getAgencyId().equals(agencyId))
                .orElseThrow(() -> new ResourceNotFoundException("CopyVariant", variantId));

        String before = variant.getStatus();
        if (nextStatus.equalsIgnoreCase(before)) {
            return CopyVariantResponse.from(variant);
        }

        variant.setStatus(nextStatus);
        variant.setUpdatedAt(OffsetDateTime.now());
        CopyVariant saved = copyVariantRepository.save(variant);

        auditService.log(agencyId, saved.getClientId(), null, TenantContextHolder.require().getRole(),
                auditAction, "COPY_VARIANT", saved.getId(),
                before, nextStatus, UUID.randomUUID().toString());

        return CopyVariantResponse.from(saved);
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

    @Transactional(readOnly = true)
    public List<CreativePackageItem> listApprovedPackageItems(UUID agencyId, UUID clientId, String objective) {
        List<CreativePackage> approved = packageRepository
                .findAllByAgencyIdAndClientIdAndStatusOrderByApprovedAtDesc(agencyId, clientId, "APPROVED");
        if (approved.isEmpty()) {
            return List.of();
        }

        CreativePackage selectedPackage = approved.stream()
            .filter(pkg -> normalizeObjective(objective).equals(normalizeObjective(pkg.getObjective())))
                .findFirst()
                .orElse(approved.get(0));

        return packageItemRepository.findAllByPackageIdOrderByCreatedAtAsc(selectedPackage.getId());
    }

    private String normalizeObjective(String objective) {
        String normalized = objective == null ? "" : objective.trim().toUpperCase(Locale.ROOT);
        Set<String> valid = Set.of(
                "OUTCOME_SALES", "OUTCOME_LEADS", "OUTCOME_TRAFFIC",
                "OUTCOME_AWARENESS", "OUTCOME_ENGAGEMENT", "OUTCOME_APP_PROMOTION"
        );
        if (valid.contains(normalized)) {
            return normalized;
        }
        return switch (normalized) {
            case "SALES", "CONVERSIONS", "PURCHASE" -> "OUTCOME_SALES";
            case "LEADS", "LEAD_GENERATION" -> "OUTCOME_LEADS";
            case "TRAFFIC", "LINK_CLICKS" -> "OUTCOME_TRAFFIC";
            case "AWARENESS", "BRAND_AWARENESS", "REACH" -> "OUTCOME_AWARENESS";
            case "ENGAGEMENT", "POST_ENGAGEMENT", "VIDEO_VIEWS" -> "OUTCOME_ENGAGEMENT";
            case "APP_INSTALLS", "APP_PROMOTION" -> "OUTCOME_APP_PROMOTION";
            default -> "";
        };
    }

    // ---- Package Detail ----

    @Transactional(readOnly = true)
    public PackageDetailResponse getPackageDetail(UUID agencyId, UUID packageId) {
        CreativePackage pkg = packageRepository.findByIdAndAgencyId(packageId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativePackage", packageId));

        List<CreativePackageItem> items = packageItemRepository.findAllByPackageIdOrderByCreatedAtAsc(packageId);

        Map<UUID, CreativeAsset> assetsById = assetRepository
                .findAllByAgencyIdAndClientId(pkg.getAgencyId(), pkg.getClientId())
                .stream()
                .collect(Collectors.toMap(CreativeAsset::getId, a -> a, (l, r) -> l));
        Map<UUID, CopyVariant> copyById = copyVariantRepository
                .findAllByAgencyIdAndClientId(pkg.getAgencyId(), pkg.getClientId())
                .stream()
                .collect(Collectors.toMap(CopyVariant::getId, v -> v, (l, r) -> l));
        Map<UUID, CreativeAnalysis> analysisByAssetId = assetsById.keySet().stream()
                .map(assetId -> analysisRepository.findByCreativeAssetId(assetId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(CreativeAnalysis::getCreativeAssetId, a -> a, (l, r) -> l));

        List<PackageDetailResponse.PackageItemDetailResponse> itemDetails = items.stream()
                .sorted(Comparator.comparing(CreativePackageItem::getCreatedAt))
                .map(item -> {
                    CreativeAsset asset = assetsById.get(item.getCreativeAssetId());
                    CopyVariant cv = copyById.get(item.getCopyVariantId());
                    CreativeAnalysis analysis = analysisByAssetId.get(item.getCreativeAssetId());

                    String thumbnailUrl = null;
                    if (asset != null && asset.getS3Key() != null && !asset.getS3Key().isBlank()) {
                        try {
                            thumbnailUrl = s3StorageService.generatePresignedGetUrl(asset.getS3Key());
                        } catch (Exception e) {
                            log.debug("Failed to generate presigned URL for asset {}: {}", asset.getId(), e.getMessage());
                        }
                    }

                    return new PackageDetailResponse.PackageItemDetailResponse(
                            item.getId(),
                            item.getPackageId(),
                            item.getCtaType(),
                            item.getDestinationUrl(),
                            item.getWeight(),
                            item.getCreatedAt(),
                            asset != null ? new PackageDetailResponse.CreativeAssetDetail(
                                    asset.getId(), asset.getOriginalFilename(), asset.getAssetType(),
                                    thumbnailUrl, asset.getStatus(), asset.getWidthPx(), asset.getHeightPx(),
                                    asset.getSizeBytes()
                            ) : null,
                            cv != null ? new PackageDetailResponse.CopyVariantDetail(
                                    cv.getId(), cv.getPrimaryText(), cv.getHeadline(),
                                    cv.getDescription(), cv.getCta(), cv.getLanguage(), cv.getStatus()
                            ) : null,
                            analysis != null && analysis.getQualityScore() != null
                                    ? analysis.getQualityScore().doubleValue() : null
                    );
                })
                .toList();

        return PackageDetailResponse.from(pkg, itemDetails);
    }

    // ---- Creatives with Variants (for package builder) ----

    @Transactional(readOnly = true)
    public List<CreativeWithVariantsResponse> listCreativesWithVariants(UUID agencyId, UUID clientId) {
        List<CreativeAsset> assets = assetRepository.findAllByAgencyIdAndClientId(agencyId, clientId)
                .stream()
                .filter(a -> "READY".equals(a.getStatus()))
                .toList();

        Map<UUID, CreativeAnalysis> analysisByAssetId = assets.stream()
                .map(a -> analysisRepository.findByCreativeAssetId(a.getId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(CreativeAnalysis::getCreativeAssetId, a -> a, (l, r) -> l));

        return assets.stream().map(asset -> {
            String thumbnailUrl = null;
            if (asset.getS3Key() != null && !asset.getS3Key().isBlank()) {
                try {
                    thumbnailUrl = s3StorageService.generatePresignedGetUrl(asset.getS3Key());
                } catch (Exception e) {
                    log.debug("Failed to generate presigned URL for asset {}: {}", asset.getId(), e.getMessage());
                }
            }
            CreativeAnalysis analysis = analysisByAssetId.get(asset.getId());
            Double score = analysis != null && analysis.getQualityScore() != null
                    ? analysis.getQualityScore().doubleValue() : null;

            List<CopyVariant> variants = copyVariantRepository.findByCreativeAssetId(asset.getId());
            return CreativeWithVariantsResponse.from(asset, thumbnailUrl, score, variants);
        }).toList();
    }

    // ---- Approved Packages List (for campaign wizard import) ----

    @Transactional(readOnly = true)
    public List<PackageDetailResponse> listApprovedPackagesWithItems(UUID agencyId, UUID clientId) {
        List<CreativePackage> approved = packageRepository
                .findAllByAgencyIdAndClientIdAndStatusOrderByApprovedAtDesc(agencyId, clientId, "APPROVED");

        return approved.stream().map(pkg -> getPackageDetail(agencyId, pkg.getId())).toList();
    }

    private CreativePackage requireEditablePackage(UUID agencyId, UUID packageId) {
        CreativePackage pkg = packageRepository.findByIdAndAgencyId(packageId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("CreativePackage", packageId));
        if (!"DRAFT".equals(pkg.getStatus())) {
            throw new IllegalStateException("Only DRAFT packages can be edited.");
        }
        return pkg;
    }

    private List<PackageItemResponse> toPackageItemResponses(CreativePackage pkg, List<CreativePackageItem> items) {
        Map<UUID, CreativeAsset> assetsById = assetRepository.findAllByAgencyIdAndClientId(pkg.getAgencyId(), pkg.getClientId())
                .stream()
                .collect(Collectors.toMap(CreativeAsset::getId, asset -> asset));
        Map<UUID, CopyVariant> copyById = copyVariantRepository.findAllByAgencyIdAndClientId(pkg.getAgencyId(), pkg.getClientId())
                .stream()
                .collect(Collectors.toMap(CopyVariant::getId, variant -> variant));
        Map<UUID, CreativeAnalysis> analysisByAssetId = assetsById.keySet().stream()
                .map(assetId -> analysisRepository.findByCreativeAssetId(assetId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(CreativeAnalysis::getCreativeAssetId, analysis -> analysis, (left, right) -> left));

        return items.stream()
                .sorted(Comparator.comparing(CreativePackageItem::getCreatedAt))
                .map(item -> toPackageItemResponse(item, assetsById.get(item.getCreativeAssetId()), copyById.get(item.getCopyVariantId()), analysisByAssetId.get(item.getCreativeAssetId())))
                .toList();
    }

    private PackageItemResponse toPackageItemResponse(CreativePackageItem item,
                                                      CreativeAsset asset,
                                                      CopyVariant copyVariant,
                                                      CreativeAnalysis analysis) {
        return new PackageItemResponse(
                item.getId(),
                item.getAgencyId(),
                item.getClientId(),
                item.getPackageId(),
                item.getCreativeAssetId(),
                item.getCopyVariantId(),
                item.getCtaType(),
                item.getDestinationUrl(),
                item.getWeight(),
                item.getCreatedAt(),
                asset != null ? AssetResponse.from(asset) : null,
                copyVariant != null ? CopyVariantResponse.from(copyVariant) : null,
                analysis != null && analysis.getQualityScore() != null ? analysis.getQualityScore().doubleValue() : null
        );
    }

    private int normalizeWeight(Integer weight) {
        int resolved = weight == null ? 50 : weight;
        return Math.max(1, Math.min(100, resolved));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
