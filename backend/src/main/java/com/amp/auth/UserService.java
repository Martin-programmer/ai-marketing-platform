package com.amp.auth;

import com.amp.agency.Agency;
import com.amp.agency.AgencyRepository;
import com.amp.common.EmailProperties;
import com.amp.common.EmailService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.clients.ClientPermissionService;
import com.amp.clients.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service handling user management operations for agency admins.
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final Set<String> VALID_ROLES = Set.of(
            "AGENCY_ADMIN", "AGENCY_USER", "CLIENT_USER"
    );

    private final UserAccountRepository userAccountRepository;
    private final AgencyRepository agencyRepository;
    private final ClientRepository clientRepository;
    private final ClientPermissionService clientPermissionService;
    private final EmailService emailService;
    private final EmailProperties emailProperties;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserAccountRepository userAccountRepository,
                       AgencyRepository agencyRepository,
                       ClientRepository clientRepository,
                       ClientPermissionService clientPermissionService,
                       EmailService emailService,
                       EmailProperties emailProperties) {
        this.userAccountRepository = userAccountRepository;
        this.agencyRepository = agencyRepository;
        this.clientRepository = clientRepository;
        this.clientPermissionService = clientPermissionService;
        this.emailService = emailService;
        this.emailProperties = emailProperties;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UUID agencyId) {
        return userAccountRepository.findAllByAgencyId(agencyId)
                .stream().map(UserResponse::from).toList();
    }

    /**
     * Invite a user to the platform via email.
     * Creates user with status INVITED (no password), generates invitation token,
     * and sends an activation email.
     */
    public UserResponse inviteUser(UUID agencyId, InviteUserRequest req) {
        if (!VALID_ROLES.contains(req.role())) {
            throw new IllegalArgumentException("Invalid role: " + req.role());
        }
        if (userAccountRepository.findByEmail(req.email()).isPresent()) {
            throw new IllegalArgumentException("Email already registered: " + req.email());
        }

        if ("CLIENT_USER".equals(req.role())) {
            if (req.clientId() == null) {
                throw new IllegalArgumentException("clientId is required for CLIENT_USER role");
            }
            clientRepository.findByIdAndAgencyId(req.clientId(), agencyId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "clientId does not belong to this agency: " + req.clientId()));
        }

        // Generate invitation token
        String invitationToken = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(72);

        UserAccount user = new UserAccount();
        user.setEmail(req.email());
        // No password — will be set on invitation acceptance
        user.setDisplayName(req.displayName() != null && !req.displayName().isBlank()
            ? req.displayName()
            : req.email().split("@")[0]);
        user.setRole(req.role());
        user.setAgencyId(agencyId);
        user.setStatus("INVITED");
        user.setCognitoSub("local-" + UUID.randomUUID());
        user.setInvitationToken(invitationToken);
        user.setInvitationExpiresAt(expiresAt);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        if ("CLIENT_USER".equals(req.role())) {
            user.setClientId(req.clientId());
        } else {
            user.setClientId(null);
        }

        user = userAccountRepository.save(user);

        if ("CLIENT_USER".equals(req.role())) {
            clientPermissionService.setUserClientPermissions(
                    user.getId(),
                    req.clientId(),
                    List.of(Permission.CLIENT_VIEW.name(), Permission.REPORTS_VIEW.name()),
                    null
            );
        }

        log.info("Created INVITED user {} with role {} for agency {}", user.getEmail(), req.role(), agencyId);

        // Send invitation email (async-safe — doesn't throw on failure)
        sendInvitationEmail(user, agencyId);

        return UserResponse.from(user);
    }

    /**
     * Generate and send a password reset email.
     */
    public void generateAndSendPasswordReset(UserAccount user) {
        String resetToken = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);

        user.setPasswordResetToken(resetToken);
        user.setPasswordResetExpiresAt(expiresAt);
        user.setUpdatedAt(OffsetDateTime.now());
        userAccountRepository.save(user);

        String resetLink = emailProperties.getBaseUrl() + "/reset-password?token=" + resetToken;

        emailService.sendTemplatedEmail(
                user.getEmail(),
                "Reset Your Password — AI Marketing Platform",
                "password-reset",
                Map.of("resetLink", resetLink)
        );

        log.info("Password reset email sent to {}", user.getEmail());
    }

    /**
     * Resolve agency name from agency ID.
     */
    @Transactional(readOnly = true)
    public String resolveAgencyName(UUID agencyId) {
        if (agencyId == null) return "Platform";
        return agencyRepository.findById(agencyId)
                .map(Agency::getName)
                .orElse("Unknown Agency");
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId, UUID agencyId) {
        UserAccount user = findOrThrow(userId);
        if (!agencyId.equals(user.getAgencyId())) {
            throw new ResourceNotFoundException("User", userId);
        }
        return UserResponse.from(user);
    }

    public UserResponse updateUser(UUID userId, UUID agencyId, UUID currentUserId,
                                   UpdateUserRequest req) {
        UserAccount user = findOrThrow(userId);
        if (!agencyId.equals(user.getAgencyId())) {
            throw new ResourceNotFoundException("User", userId);
        }

        // Cannot change own role or disable self
        if (userId.equals(currentUserId)) {
            if (req.role() != null && !req.role().equals(user.getRole())) {
                throw new IllegalArgumentException("Cannot change your own role");
            }
            if ("DISABLED".equals(req.status())) {
                throw new IllegalArgumentException("Cannot disable your own account");
            }
        }

        if (req.displayName() != null && !req.displayName().isBlank()) {
            user.setDisplayName(req.displayName());
        }
        if (req.role() != null && !req.role().isBlank()) {
            if (!VALID_ROLES.contains(req.role())) {
                throw new IllegalArgumentException("Invalid role: " + req.role());
            }
            user.setRole(req.role());
        }
        if (req.status() != null && !req.status().isBlank()) {
            if (!Set.of("ACTIVE", "DISABLED").contains(req.status())) {
                throw new IllegalArgumentException("Invalid status: " + req.status());
            }
            user.setStatus(req.status());
        }

        user = userAccountRepository.save(user);
        return UserResponse.from(user);
    }

    public UserResponse disableUser(UUID userId, UUID agencyId, UUID currentUserId) {
        if (userId.equals(currentUserId)) {
            throw new IllegalArgumentException("Cannot disable your own account");
        }
        UserAccount user = findOrThrow(userId);
        if (!agencyId.equals(user.getAgencyId())) {
            throw new ResourceNotFoundException("User", userId);
        }
        user.setStatus("DISABLED");
        user = userAccountRepository.save(user);
        return UserResponse.from(user);
    }

    public UserResponse enableUser(UUID userId, UUID agencyId) {
        UserAccount user = findOrThrow(userId);
        if (!agencyId.equals(user.getAgencyId())) {
            throw new ResourceNotFoundException("User", userId);
        }
        user.setStatus("ACTIVE");
        user = userAccountRepository.save(user);
        return UserResponse.from(user);
    }

    // ── Private Helpers ────────────────────────────────────────

    private void sendInvitationEmail(UserAccount user, UUID agencyId) {
        String agencyName = resolveAgencyName(agencyId);
        String activationLink = emailProperties.getBaseUrl()
                + "/accept-invite?token=" + user.getInvitationToken();

        emailService.sendTemplatedEmail(
                user.getEmail(),
                "You're Invited to AI Marketing Platform!",
                "invitation",
                Map.of(
                        "agencyName", agencyName,
                        "role", user.getRole(),
                        "activationLink", activationLink
                )
        );
    }

    private UserAccount findOrThrow(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
