package com.amp.agency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for agency management (Owner Admin only).
 */
@RestController
@RequestMapping("/api/v1/admin/agencies")
public class AgencyController {

    private final AgencyService agencyService;

    public AgencyController(AgencyService agencyService) {
        this.agencyService = agencyService;
    }

    private ResponseEntity<?> requireOwnerAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("currentUserRole");
        if (!"OWNER_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Owner Admin access required"));
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<?> listAgencies(HttpServletRequest request) {
        ResponseEntity<?> denied = requireOwnerAdmin(request);
        if (denied != null) return denied;

        List<AgencyResponse> agencies = agencyService.listAgencies();
        return ResponseEntity.ok(agencies);
    }

    @PostMapping
    public ResponseEntity<?> createAgency(@Valid @RequestBody CreateAgencyWithAdminRequest req,
                                          HttpServletRequest request) {
        ResponseEntity<?> denied = requireOwnerAdmin(request);
        if (denied != null) return denied;

        CreateAgencyWithAdminResponse response = agencyService.createAgencyWithAdmin(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{agencyId}")
    public ResponseEntity<?> getAgency(@PathVariable UUID agencyId,
                                       HttpServletRequest request) {
        ResponseEntity<?> denied = requireOwnerAdmin(request);
        if (denied != null) return denied;

        return ResponseEntity.ok(agencyService.getAgency(agencyId));
    }

    @PatchMapping("/{agencyId}")
    public ResponseEntity<?> updateAgency(@PathVariable UUID agencyId,
                                          @RequestBody UpdateAgencyRequest req,
                                          HttpServletRequest request) {
        ResponseEntity<?> denied = requireOwnerAdmin(request);
        if (denied != null) return denied;

        return ResponseEntity.ok(agencyService.updateAgency(agencyId, req));
    }

    @PostMapping("/{agencyId}/suspend")
    public ResponseEntity<?> suspendAgency(@PathVariable UUID agencyId,
                                           HttpServletRequest request) {
        ResponseEntity<?> denied = requireOwnerAdmin(request);
        if (denied != null) return denied;

        return ResponseEntity.ok(agencyService.suspendAgency(agencyId));
    }

    @PostMapping("/{agencyId}/reactivate")
    public ResponseEntity<?> reactivateAgency(@PathVariable UUID agencyId,
                                              HttpServletRequest request) {
        ResponseEntity<?> denied = requireOwnerAdmin(request);
        if (denied != null) return denied;

        return ResponseEntity.ok(agencyService.reactivateAgency(agencyId));
    }
}
