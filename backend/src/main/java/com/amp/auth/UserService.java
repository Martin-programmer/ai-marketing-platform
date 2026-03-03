package com.amp.auth;

import com.amp.common.exception.ResourceNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service handling user management operations for agency admins.
 */
@Service
@Transactional
public class UserService {

    private static final Set<String> VALID_ROLES = Set.of(
            "AGENCY_ADMIN", "AGENCY_USER", "CLIENT_USER"
    );

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UUID agencyId) {
        return userAccountRepository.findAllByAgencyId(agencyId)
                .stream().map(UserResponse::from).toList();
    }

    public UserResponse inviteUser(UUID agencyId, InviteUserRequest req) {
        if (!VALID_ROLES.contains(req.role())) {
            throw new IllegalArgumentException("Invalid role: " + req.role());
        }
        if ("CLIENT_USER".equals(req.role()) && req.clientId() == null) {
            throw new IllegalArgumentException("clientId is required for CLIENT_USER role");
        }
        if (userAccountRepository.findByEmail(req.email()).isPresent()) {
            throw new IllegalArgumentException("Email already registered: " + req.email());
        }

        UserAccount user = new UserAccount();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName());
        user.setRole(req.role());
        user.setAgencyId(agencyId);
        user.setStatus("ACTIVE");
        user.setCognitoSub("local-" + UUID.randomUUID());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        if ("CLIENT_USER".equals(req.role())) {
            user.setClientId(req.clientId());
        }

        user = userAccountRepository.save(user);
        return UserResponse.from(user);
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

    private UserAccount findOrThrow(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
