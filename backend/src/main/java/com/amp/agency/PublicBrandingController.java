package com.amp.agency;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Public (no-auth) endpoint for retrieving branding information.
 * Used by the login page when a client arrives from an email link.
 */
@RestController
@RequestMapping("/api/v1/public/branding")
public class PublicBrandingController {

    private final AgencyBrandingService brandingService;

    public PublicBrandingController(AgencyBrandingService brandingService) {
        this.brandingService = brandingService;
    }

    /**
     * GET /api/v1/public/branding?context=agency&agencyId={id}
     *   → returns agency branding (name, logo, colors)
     *
     * GET /api/v1/public/branding?context=platform
     *   → returns Adverion platform branding from system_setting
     */
    @GetMapping
    public ResponseEntity<?> getBranding(
            @RequestParam(defaultValue = "platform") String context,
            @RequestParam(required = false) UUID agencyId) {

        if ("agency".equalsIgnoreCase(context)) {
            if (agencyId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("code", "MISSING_AGENCY_ID",
                                "message", "agencyId is required when context=agency"));
            }
            PublicBrandingResponse branding = brandingService.getPublicAgencyBranding(agencyId);
            return ResponseEntity.ok(branding);
        }

        // Default: platform branding
        PublicBrandingResponse branding = brandingService.getPlatformBranding();
        return ResponseEntity.ok(branding);
    }

    @GetMapping("/agency/{slug}")
    public ResponseEntity<PublicBrandingResponse> getAgencyBrandingBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(brandingService.getPublicAgencyBrandingBySlug(slug));
    }
}
