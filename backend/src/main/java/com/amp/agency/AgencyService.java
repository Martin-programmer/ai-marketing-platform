package com.amp.agency;

import com.amp.auth.InviteUserRequest;
import com.amp.auth.UserResponse;
import com.amp.auth.UserService;
import com.amp.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service handling agency CRUD and lifecycle operations.
 */
@Service
@Transactional
public class AgencyService {

    private static final Logger log = LoggerFactory.getLogger(AgencyService.class);

    private final AgencyRepository agencyRepository;
    private final UserService userService;

    public AgencyService(AgencyRepository agencyRepository,
                         UserService userService) {
        this.agencyRepository = agencyRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<AgencyResponse> listAgencies() {
        return agencyRepository.findAll()
                .stream().map(AgencyResponse::from).toList();
    }

    public AgencyResponse createAgency(CreateAgencyRequest req) {
        Agency a = new Agency();
        a.setName(req.name());
        a.setStatus("ACTIVE");
        a.setPlanCode(req.planCode());
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());

        Agency saved = agencyRepository.save(a);
        return AgencyResponse.from(saved);
    }

    /**
     * Create a new agency together with its first AGENCY_ADMIN user.
     * The admin receives an invitation email instead of having a password set directly.
     */
    public CreateAgencyWithAdminResponse createAgencyWithAdmin(CreateAgencyWithAdminRequest req) {
        Agency a = new Agency();
        a.setName(req.name());
        a.setStatus("ACTIVE");
        a.setPlanCode(req.planCode());
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());
        Agency saved = agencyRepository.save(a);

        CreateAgencyWithAdminResponse.AdminUserInfo adminUser = null;
        if (req.adminEmail() != null && !req.adminEmail().isBlank()) {
            UserResponse invited = userService.inviteUser(saved.getId(), new InviteUserRequest(
                req.adminEmail(),
                req.adminDisplayName(),
                "AGENCY_ADMIN",
                null
            ));
            adminUser = new CreateAgencyWithAdminResponse.AdminUserInfo(
                invited.id(), invited.email(), invited.role()
            );
            log.info("Created agency '{}' and invited admin {}", saved.getName(), invited.email());
        } else {
            log.info("Created agency '{}' without initial admin invitation", saved.getName());
        }

        return new CreateAgencyWithAdminResponse(
                AgencyResponse.from(saved),
            adminUser
        );
    }

    @Transactional(readOnly = true)
    public AgencyResponse getAgency(UUID agencyId) {
        Agency a = findOrThrow(agencyId);
        return AgencyResponse.from(a);
    }

    public AgencyResponse updateAgency(UUID agencyId, UpdateAgencyRequest req) {
        Agency a = findOrThrow(agencyId);

        if (req.name() != null && !req.name().isBlank()) {
            a.setName(req.name());
        }
        if (req.planCode() != null && !req.planCode().isBlank()) {
            a.setPlanCode(req.planCode());
        }

        Agency saved = agencyRepository.save(a);
        return AgencyResponse.from(saved);
    }

    public AgencyResponse suspendAgency(UUID agencyId) {
        Agency a = findOrThrow(agencyId);
        a.setStatus("SUSPENDED");
        Agency saved = agencyRepository.save(a);
        return AgencyResponse.from(saved);
    }

    public AgencyResponse reactivateAgency(UUID agencyId) {
        Agency a = findOrThrow(agencyId);
        a.setStatus("ACTIVE");
        Agency saved = agencyRepository.save(a);
        return AgencyResponse.from(saved);
    }

    private Agency findOrThrow(UUID agencyId) {
        return agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agency", agencyId));
    }
}
