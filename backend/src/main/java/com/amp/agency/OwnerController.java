package com.amp.agency;

import com.amp.ai.AgencyIntelligenceService;
import com.amp.auth.InviteUserRequest;
import com.amp.auth.UserResponse;
import com.amp.auth.UserService;
import com.amp.auth.UserAccountRepository;
import com.amp.common.RoleGuard;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owner Admin endpoints for platform management.
 * Only accessible by OWNER_ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/owner")
public class OwnerController {

    private final AgencyRepository agencyRepository;
    private final UserAccountRepository userAccountRepository;
    private final AgencyIntelligenceService intelligenceService;
    private final AgencyService agencyService;
    private final UserService userService;

    public OwnerController(AgencyRepository agencyRepository,
                           UserAccountRepository userAccountRepository,
                           AgencyIntelligenceService intelligenceService,
                           AgencyService agencyService,
                           UserService userService) {
        this.agencyRepository = agencyRepository;
        this.userAccountRepository = userAccountRepository;
        this.intelligenceService = intelligenceService;
        this.agencyService = agencyService;
        this.userService = userService;
    }

    @GetMapping("/agencies")
    public ResponseEntity<?> listAgencies() {
        RoleGuard.requireOwnerAdmin();
        List<Agency> agencies = agencyRepository.findAll();
        return ResponseEntity.ok(agencies.stream().map(a -> Map.of(
                "id", a.getId(),
                "name", a.getName(),
                "status", a.getStatus(),
                "planCode", a.getPlanCode(),
                "createdAt", a.getCreatedAt()
        )).toList());
    }

    @PostMapping("/agencies")
    public ResponseEntity<?> createAgency(@Valid @RequestBody CreateAgencyWithAdminRequest request) {
        RoleGuard.requireOwnerAdmin();
        CreateAgencyWithAdminResponse response = agencyService.createAgencyWithAdmin(request);
        return ResponseEntity.status(201).body(response);
    }

    @PatchMapping("/agencies/{agencyId}")
    public ResponseEntity<?> updateAgency(@PathVariable UUID agencyId,
                                          @RequestBody Map<String, String> request) {
        RoleGuard.requireOwnerAdmin();
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        if (request.containsKey("name")) agency.setName(request.get("name"));
        if (request.containsKey("status")) agency.setStatus(request.get("status"));
        if (request.containsKey("planCode")) agency.setPlanCode(request.get("planCode"));
        agency.setUpdatedAt(OffsetDateTime.now());
        agencyRepository.save(agency);
        return ResponseEntity.ok(Map.of(
                "id", agency.getId(),
                "name", agency.getName(),
                "status", agency.getStatus()
        ));
    }

    @GetMapping("/agencies/{agencyId}/users")
    public ResponseEntity<?> listAgencyUsers(@PathVariable UUID agencyId) {
        RoleGuard.requireOwnerAdmin();
        var users = userAccountRepository.findAllByAgencyId(agencyId);
        return ResponseEntity.ok(users.stream().map(u -> Map.of(
                "id", u.getId(),
                "email", u.getEmail(),
                "role", u.getRole(),
                "status", u.getStatus(),
                "displayName", u.getDisplayName() != null ? u.getDisplayName() : "",
                "createdAt", u.getCreatedAt()
        )).toList());
    }

    @PostMapping("/agencies/{agencyId}/users")
    public ResponseEntity<?> inviteAgencyUser(@PathVariable UUID agencyId,
                                              @Valid @RequestBody InviteUserRequest request) {
        RoleGuard.requireOwnerAdmin();
        try {
            UserResponse invited = userService.inviteUser(agencyId, request);
            return ResponseEntity.status(201).body(invited);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_REQUEST",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> ownerDashboard() {
        RoleGuard.requireOwnerAdmin();
        long agencyCount = agencyRepository.count();
        long userCount = userAccountRepository.count();
        return ResponseEntity.ok(Map.of(
                "totalAgencies", agencyCount,
                "totalUsers", userCount
        ));
    }

    @GetMapping("/intelligence")
    public ResponseEntity<?> intelligence() {
        RoleGuard.requireOwnerAdmin();
        AgencyIntelligenceService.IntelligenceReport report = intelligenceService.generateIntelligence();
        return ResponseEntity.ok(report);
    }
}
