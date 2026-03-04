package com.amp.creatives;

import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.tenancy.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for creative assets and packages (API v1).
 */
@RestController
@RequestMapping("/api/v1")
public class CreativeController {

    private final CreativeService creativeService;
    private final AccessControl accessControl;

    public CreativeController(CreativeService creativeService, AccessControl accessControl) {
        this.creativeService = creativeService;
        this.accessControl = accessControl;
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

    // ---- Packages ----

    @GetMapping("/clients/{clientId}/creative-packages")
    public ResponseEntity<List<PackageResponse>> listPackages(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.CREATIVES_VIEW);
        return ResponseEntity.ok(creativeService.listPackages(agencyId(), clientId));
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
}
