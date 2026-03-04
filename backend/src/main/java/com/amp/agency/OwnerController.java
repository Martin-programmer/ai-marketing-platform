package com.amp.agency;

import com.amp.auth.AccessControl;
import com.amp.auth.UserAccountRepository;
import com.amp.common.RoleGuard;
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
    private final AccessControl accessControl;

    public OwnerController(AgencyRepository agencyRepository,
                           UserAccountRepository userAccountRepository,
                           AccessControl accessControl) {
        this.agencyRepository = agencyRepository;
        this.userAccountRepository = userAccountRepository;
        this.accessControl = accessControl;
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
    public ResponseEntity<?> createAgency(@RequestBody Map<String, String> request) {
        RoleGuard.requireOwnerAdmin();
        Agency agency = new Agency();
        agency.setName(request.get("name"));
        agency.setStatus("ACTIVE");
        agency.setPlanCode(request.getOrDefault("planCode", "STARTER"));
        agency.setCreatedAt(OffsetDateTime.now());
        agency.setUpdatedAt(OffsetDateTime.now());
        agency = agencyRepository.save(agency);
        return ResponseEntity.status(201).body(Map.of(
                "id", agency.getId(),
                "name", agency.getName(),
                "status", agency.getStatus()
        ));
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
                "displayName", u.getDisplayName() != null ? u.getDisplayName() : ""
        )).toList());
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
}
