package com.amp.agency;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for agency settings (AGENCY_ADMIN + OWNER_ADMIN).
 * <p>
 * Operates on the caller's own agency (resolved from JWT agencyId).
 */
@RestController
@RequestMapping("/api/v1/agency/settings")
public class AgencySettingsController {

    private final AgencyService agencyService;

    public AgencySettingsController(AgencyService agencyService) {
        this.agencyService = agencyService;
    }

    /**
     * GET /api/v1/agency/settings – return current agency settings.
     */
    @GetMapping
    public ResponseEntity<?> getSettings(HttpServletRequest request) {
        ResponseEntity<?> denied = requireAgencyAdmin(request);
        if (denied != null) return denied;

        UUID agencyId = (UUID) request.getAttribute("currentUserAgencyId");
        AgencyResponse agency = agencyService.getAgency(agencyId);

        return ResponseEntity.ok(Map.of(
                "twoFactorEnabled", agency.twoFactorEnabled()
        ));
    }

    /**
     * PATCH /api/v1/agency/settings – update agency settings.
     * Body: { "twoFactorEnabled": true/false }
     */
    @PatchMapping
    public ResponseEntity<?> updateSettings(@RequestBody UpdateAgencySettingsRequest req,
                                            HttpServletRequest request) {
        ResponseEntity<?> denied = requireAgencyAdmin(request);
        if (denied != null) return denied;

        UUID agencyId = (UUID) request.getAttribute("currentUserAgencyId");
        agencyService.updateTwoFactorEnabled(agencyId, req.twoFactorEnabled());

        return ResponseEntity.ok(Map.of(
                "twoFactorEnabled", req.twoFactorEnabled(),
                "message", "Agency settings updated"
        ));
    }

    public record UpdateAgencySettingsRequest(boolean twoFactorEnabled) {}

    private ResponseEntity<?> requireAgencyAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("currentUserRole");
        if (!"AGENCY_ADMIN".equals(role) && !"OWNER_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Agency Admin access required"));
        }

        Object agencyIdObj = request.getAttribute("currentUserAgencyId");
        if (agencyIdObj == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "NO_AGENCY", "message", "No agency associated with this account"));
        }

        return null;
    }
}
