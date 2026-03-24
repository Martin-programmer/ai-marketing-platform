package com.amp.agency;

import com.amp.common.RoleGuard;
import com.amp.creatives.S3StorageService;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for agency branding management (AGENCY_ADMIN only).
 */
@RestController
@RequestMapping("/api/v1/agency/branding")
public class AgencyBrandingController {

    private static final Logger log = LoggerFactory.getLogger(AgencyBrandingController.class);

    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024; // 2 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/svg+xml"
    );
    private static final Map<String, String> CONTENT_TYPE_TO_EXT = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/svg+xml", "svg"
    );

    private final AgencyBrandingService brandingService;
    private final S3StorageService s3StorageService;

    public AgencyBrandingController(AgencyBrandingService brandingService,
                                    S3StorageService s3StorageService) {
        this.brandingService = brandingService;
        this.s3StorageService = s3StorageService;
    }

    /**
     * GET /api/v1/agency/branding — returns all branding fields for current agency.
     */
    @GetMapping
    public ResponseEntity<AgencyBrandingResponse> getBranding() {
        RoleGuard.requireAgencyAdmin();
        UUID agencyId = requireAgencyId();

        return ResponseEntity.ok(brandingService.getAgencyBranding(agencyId));
    }

    @GetMapping("/check-slug")
    public ResponseEntity<AgencyBrandingService.SlugAvailabilityResponse> checkSlug(@RequestParam String slug) {
        RoleGuard.requireAgencyAdmin();
        UUID agencyId = requireAgencyId();
        return ResponseEntity.ok(brandingService.checkSlugAvailability(agencyId, slug));
    }

    /**
     * PUT /api/v1/agency/branding — updates branding fields (colors, texts, etc.).
     */
    @PutMapping
    public ResponseEntity<AgencyBrandingResponse> updateBranding(@RequestBody UpdateBrandingRequest req) {
        RoleGuard.requireAgencyAdmin();
        UUID agencyId = requireAgencyId();

        AgencyBrandingResponse updated = brandingService.updateBranding(agencyId, req);
        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/v1/agency/branding/logo — uploads agency logo (max 2 MB, PNG/JPG/SVG).
     */
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadLogo(@RequestParam("file") MultipartFile file) {
        RoleGuard.requireAgencyAdmin();
        UUID agencyId = requireAgencyId();

        // Validate file size
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "EMPTY_FILE", "message", "No file uploaded"));
        }
        if (file.getSize() > MAX_LOGO_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("code", "FILE_TOO_LARGE", "message", "Logo must be under 2 MB"));
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_TYPE",
                            "message", "Logo must be PNG, JPG, or SVG"));
        }

        String ext = CONTENT_TYPE_TO_EXT.getOrDefault(contentType, "png");
        String s3Key = String.format("agencies/%s/branding/logo.%s", agencyId, ext);

        try {
            byte[] bytes = file.getBytes();
            s3StorageService.uploadBytes(s3Key, bytes, contentType);

            String logoUrl = s3StorageService.generatePresignedGetUrl(s3Key);
            brandingService.saveLogo(agencyId, s3Key, logoUrl);

            log.info("Agency {} logo uploaded: {} ({} bytes)", agencyId, s3Key, bytes.length);
            return ResponseEntity.ok(Map.of("logoUrl", logoUrl));
        } catch (Exception e) {
            log.error("Failed to upload logo for agency {}: {}", agencyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "UPLOAD_FAILED", "message", "Logo upload failed"));
        }
    }

    private UUID requireAgencyId() {
        TenantContext ctx = TenantContextHolder.require();
        UUID agencyId = ctx.getAgencyId();
        if (agencyId == null) {
            throw new IllegalStateException("No agency associated with this account");
        }
        return agencyId;
    }
}
