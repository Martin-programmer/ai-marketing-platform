package com.amp.creatives;

import com.amp.ai.CopyFactoryService;
import com.amp.ai.CreativeAnalyzerService;
import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for creative assets and packages (API v1).
 */
@RestController
@RequestMapping("/api/v1")
public class CreativeController {

    private final CreativeService creativeService;
    private final AccessControl accessControl;
    private final CreativeAnalyzerService creativeAnalyzerService;
    private final CopyFactoryService copyFactoryService;
    private final CreativeAssetRepository assetRepository;
    private final CreativeAnalysisRepository analysisRepository;

    public CreativeController(CreativeService creativeService,
                               AccessControl accessControl,
                               CreativeAnalyzerService creativeAnalyzerService,
                               CopyFactoryService copyFactoryService,
                               CreativeAssetRepository assetRepository,
                               CreativeAnalysisRepository analysisRepository) {
        this.creativeService = creativeService;
        this.accessControl = accessControl;
        this.creativeAnalyzerService = creativeAnalyzerService;
        this.copyFactoryService = copyFactoryService;
        this.assetRepository = assetRepository;
        this.analysisRepository = analysisRepository;
    }

    private UUID agencyId() {
        return TenantContextHolder.require().getAgencyId();
    }

    // ---- Assets ----

    @GetMapping("/clients/{clientId}/creatives")
    public ResponseEntity<List<AssetResponse>> listAssets(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        return ResponseEntity.ok(creativeService.listAssets(agencyId(), clientId));
    }

    @PostMapping("/clients/{clientId}/creatives")
    public ResponseEntity<AssetResponse> createAsset(
            @PathVariable UUID clientId,
            @Valid @RequestBody CreateAssetRequest request) {

        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        AssetResponse created = creativeService.createAsset(agencyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/creatives/{assetId}")
    public ResponseEntity<AssetResponse> getAsset(@PathVariable UUID assetId) {
        UUID clientId = creativeService.resolveAssetClientId(agencyId(), assetId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        return ResponseEntity.ok(creativeService.getAsset(agencyId(), assetId));
    }

    @GetMapping("/creatives/{assetId}/analysis")
    public ResponseEntity<CreativeAnalysis> getAnalysis(@PathVariable UUID assetId) {
        UUID clientId = creativeService.resolveAssetClientId(agencyId(), assetId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        CreativeAnalysis analysis = creativeService.getAnalysis(assetId);
        if (analysis == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(analysis);
    }

    // ---- S3 Upload Flow ----

    /** Initiate a creative upload — returns presigned PUT URL. */
    @PostMapping("/clients/{clientId}/creatives/uploads")
    public ResponseEntity<UploadInitResponse> initiateUpload(
            @PathVariable UUID clientId,
            @Valid @RequestBody UploadInitRequest request) {
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        UploadInitResponse response = creativeService.initiateUpload(agencyId(), clientId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Complete a creative upload — marks asset as READY. */
    @PostMapping("/creatives/{assetId}/uploads/complete")
    public ResponseEntity<AssetResponse> completeUpload(
            @PathVariable UUID assetId,
            @RequestBody(required = false) UploadCompleteRequest request) {
        RoleGuard.requireAgencyRole();
        AssetResponse response = creativeService.completeUpload(agencyId(), assetId, request);
        return ResponseEntity.ok(response);
    }

    /** Get presigned GET URL for viewing an asset. */
    @GetMapping("/creatives/{assetId}/url")
    public ResponseEntity<Map<String, Object>> getViewUrl(@PathVariable UUID assetId) {
        RoleGuard.requireAgencyRole();
        String url = creativeService.getPresignedViewUrl(agencyId(), assetId);
        return ResponseEntity.ok(Map.of("url", url, "expiresInMinutes", 60));
    }

    /** Delete a creative asset and its S3 object. */
    @DeleteMapping("/creatives/{assetId}")
    public ResponseEntity<Map<String, String>> deleteAsset(@PathVariable UUID assetId) {
        RoleGuard.requireAgencyRole();
        creativeService.deleteAsset(agencyId(), assetId);
        return ResponseEntity.ok(Map.of("message", "Asset deleted"));
    }

    // ---- Packages ----

    @GetMapping("/clients/{clientId}/creative-packages")
    public ResponseEntity<List<PackageResponse>> listPackages(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        return ResponseEntity.ok(creativeService.listPackages(agencyId(), clientId));
    }

    @GetMapping("/creative-packages/{packageId}")
    public ResponseEntity<PackageDetailResponse> getPackage(@PathVariable UUID packageId) {
        UUID clientId = creativeService.resolvePackageClientId(agencyId(), packageId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        return ResponseEntity.ok(creativeService.getPackageDetail(agencyId(), packageId));
    }

    @PostMapping("/clients/{clientId}/creative-packages")
    public ResponseEntity<PackageResponse> createPackage(
            @PathVariable UUID clientId,
            @Valid @RequestBody CreatePackageRequest request) {

        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        PackageResponse created = creativeService.createPackage(agencyId(), clientId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/creative-packages/{packageId}/submit")
    public ResponseEntity<PackageResponse> submitPackage(@PathVariable UUID packageId) {
        UUID clientId = creativeService.resolvePackageClientId(agencyId(), packageId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        return ResponseEntity.ok(creativeService.submitPackage(agencyId(), packageId));
    }

    @PostMapping("/creative-packages/{packageId}/approve")
    public ResponseEntity<PackageResponse> approvePackage(@PathVariable UUID packageId) {
        UUID clientId = creativeService.resolvePackageClientId(agencyId(), packageId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        return ResponseEntity.ok(creativeService.approvePackage(agencyId(), packageId));
    }

    @PostMapping("/creative-packages/{packageId}/reject")
    public ResponseEntity<PackageResponse> rejectPackage(@PathVariable UUID packageId) {
        UUID clientId = creativeService.resolvePackageClientId(agencyId(), packageId);
        accessControl.requireClientPermission(clientId, Permission.AI_APPROVE);
        return ResponseEntity.ok(creativeService.rejectPackage(agencyId(), packageId));
    }

    @PutMapping("/creative-packages/{packageId}")
    public ResponseEntity<PackageResponse> updatePackage(@PathVariable UUID packageId,
                                                         @Valid @RequestBody UpdatePackageRequest request) {
        UUID clientId = creativeService.resolvePackageClientId(agencyId(), packageId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        return ResponseEntity.ok(creativeService.updatePackage(agencyId(), packageId, request));
    }

    @GetMapping("/creative-packages/{packageId}/items")
    public ResponseEntity<List<PackageItemResponse>> listPackageItems(@PathVariable UUID packageId) {
        UUID clientId = creativeService.resolvePackageClientId(agencyId(), packageId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        return ResponseEntity.ok(creativeService.listPackageItems(agencyId(), packageId));
    }

    @PostMapping("/creative-packages/{packageId}/items")
    public ResponseEntity<PackageItemResponse> addPackageItem(@PathVariable UUID packageId,
                                                              @Valid @RequestBody CreatePackageItemRequest request) {
        UUID clientId = creativeService.resolvePackageClientId(agencyId(), packageId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        return ResponseEntity.status(HttpStatus.CREATED).body(creativeService.addPackageItem(agencyId(), packageId, request));
    }

    @DeleteMapping("/creative-packages/{packageId}/items/{itemId}")
    public ResponseEntity<Void> deletePackageItem(@PathVariable UUID packageId, @PathVariable UUID itemId) {
        UUID clientId = creativeService.resolvePackageClientId(agencyId(), packageId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        creativeService.deletePackageItem(agencyId(), packageId, itemId);
        return ResponseEntity.noContent().build();
    }

    // ---- Creatives with Variants (Package Builder) ----

    @GetMapping("/clients/{clientId}/creatives/with-variants")
    public ResponseEntity<List<CreativeWithVariantsResponse>> listCreativesWithVariants(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        return ResponseEntity.ok(creativeService.listCreativesWithVariants(agencyId(), clientId));
    }

    // ---- Approved Packages (Campaign Wizard Import) ----

    @GetMapping("/clients/{clientId}/creative-packages/approved")
    public ResponseEntity<List<PackageDetailResponse>> listApprovedPackages(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        return ResponseEntity.ok(creativeService.listApprovedPackagesWithItems(agencyId(), clientId));
    }

    // ---- Copy Variants ----

    @GetMapping("/clients/{clientId}/copy-variants")
    public ResponseEntity<List<CopyVariantResponse>> listCopyVariants(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        return ResponseEntity.ok(creativeService.listCopyVariants(agencyId(), clientId));
    }

    @PostMapping("/clients/{clientId}/copy-variants")
    public ResponseEntity<CopyVariantResponse> createCopyVariant(
            @PathVariable UUID clientId,
            @Valid @RequestBody CreateCopyVariantRequest request) {
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        CopyVariantResponse created = creativeService.createCopyVariant(agencyId(), clientId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/copy-variants/{variantId}")
    public ResponseEntity<CopyVariantResponse> getCopyVariant(@PathVariable UUID variantId) {
        UUID clientId = creativeService.resolveCopyVariantClientId(agencyId(), variantId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        return ResponseEntity.ok(creativeService.getCopyVariant(agencyId(), variantId));
    }

    @PostMapping("/copy-variants/{variantId}/approve")
    public ResponseEntity<CopyVariantResponse> approveCopyVariant(@PathVariable UUID variantId) {
        UUID clientId = creativeService.resolveCopyVariantClientId(agencyId(), variantId);
        accessControl.requireClientPermission(clientId, Permission.AI_APPROVE);
        return ResponseEntity.ok(creativeService.approveCopyVariant(agencyId(), variantId));
    }

    @PostMapping("/copy-variants/{variantId}/reject")
    public ResponseEntity<CopyVariantResponse> rejectCopyVariant(@PathVariable UUID variantId) {
        UUID clientId = creativeService.resolveCopyVariantClientId(agencyId(), variantId);
        accessControl.requireClientPermission(clientId, Permission.AI_APPROVE);
        return ResponseEntity.ok(creativeService.rejectCopyVariant(agencyId(), variantId));
    }

    // ---- AI Endpoints ----

    /** Manually trigger AI analysis on a creative asset. */
    @PostMapping("/creatives/{assetId}/analyze")
    public ResponseEntity<Map<String, Object>> analyzeAsset(@PathVariable UUID assetId) {
        UUID clientId = creativeService.resolveAssetClientId(agencyId(), assetId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        CreativeAnalysis analysis = creativeAnalyzerService.analyzeById(agencyId(), assetId, assetRepository);
        if (analysis == null) {
            return ResponseEntity.ok(Map.of("status", "skipped",
                    "message", "Analysis not performed (disabled or not an image)"));
        }
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "analysisId", analysis.getId(),
                "qualityScore", analysis.getQualityScore()));
    }

    /** Manually trigger AI copy generation for a creative asset (requires analysis first). */
    @PostMapping("/creatives/{assetId}/generate-copy")
    public ResponseEntity<List<CopyVariantResponse>> generateCopy(@PathVariable UUID assetId) {
        UUID clientId = creativeService.resolveAssetClientId(agencyId(), assetId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_EDIT);
        List<CopyVariant> variants = copyFactoryService.generateCopyForAsset(
                agencyId(), assetId, analysisRepository);
        List<CopyVariantResponse> response = variants.stream()
                .map(CopyVariantResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /** Get copy variants generated for a specific creative asset. */
    @GetMapping("/creatives/{assetId}/copy-variants")
    public ResponseEntity<List<CopyVariantResponse>> getCopyVariantsForAsset(@PathVariable UUID assetId) {
        UUID clientId = creativeService.resolveAssetClientId(agencyId(), assetId);
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        List<CopyVariantResponse> response = creativeService.listCopyVariantsForAsset(assetId);
        return ResponseEntity.ok(response);
    }
}
