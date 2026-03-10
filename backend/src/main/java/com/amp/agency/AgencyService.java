package com.amp.agency;

import com.amp.auth.UserAccount;
import com.amp.auth.UserAccountRepository;
import com.amp.common.EmailProperties;
import com.amp.common.EmailService;
import com.amp.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service handling agency CRUD and lifecycle operations.
 */
@Service
@Transactional
public class AgencyService {

    private static final Logger log = LoggerFactory.getLogger(AgencyService.class);

    private final AgencyRepository agencyRepository;
    private final UserAccountRepository userAccountRepository;
    private final EmailService emailService;
    private final EmailProperties emailProperties;

    public AgencyService(AgencyRepository agencyRepository,
                         UserAccountRepository userAccountRepository,
                         EmailService emailService,
                         EmailProperties emailProperties) {
        this.agencyRepository = agencyRepository;
        this.userAccountRepository = userAccountRepository;
        this.emailService = emailService;
        this.emailProperties = emailProperties;
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
        // Create the agency
        Agency a = new Agency();
        a.setName(req.name());
        a.setStatus("ACTIVE");
        a.setPlanCode(req.planCode());
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());
        Agency saved = agencyRepository.save(a);

        // Generate invitation token
        String invitationToken = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(72);

        // Create the admin user with INVITED status (no password)
        UserAccount admin = new UserAccount();
        admin.setEmail(req.adminEmail());
        admin.setDisplayName(req.adminDisplayName());
        admin.setRole("AGENCY_ADMIN");
        admin.setAgencyId(saved.getId());
        admin.setStatus("INVITED");
        admin.setCognitoSub("local-" + UUID.randomUUID());
        admin.setInvitationToken(invitationToken);
        admin.setInvitationExpiresAt(expiresAt);
        admin.setCreatedAt(OffsetDateTime.now());
        admin.setUpdatedAt(OffsetDateTime.now());
        admin = userAccountRepository.save(admin);

        log.info("Created agency '{}' with invited admin {}", saved.getName(), admin.getEmail());

        // Send invitation email
        String activationLink = emailProperties.getBaseUrl()
                + "/accept-invite?token=" + invitationToken;
        emailService.sendTemplatedEmail(
                admin.getEmail(),
                "You're Invited to AI Marketing Platform!",
                "invitation",
                Map.of(
                        "agencyName", saved.getName(),
                        "role", "AGENCY_ADMIN",
                        "activationLink", activationLink
                )
        );

        return new CreateAgencyWithAdminResponse(
                AgencyResponse.from(saved),
                new CreateAgencyWithAdminResponse.AdminUserInfo(
                        admin.getId(), admin.getEmail(), admin.getRole()
                )
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
