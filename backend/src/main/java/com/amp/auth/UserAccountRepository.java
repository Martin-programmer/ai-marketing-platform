package com.amp.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link UserAccount}.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    List<UserAccount> findAllByAgencyId(UUID agencyId);

    boolean existsByClientId(UUID clientId);

    List<UserAccount> findAllByClientIdAndStatus(UUID clientId, String status);

    List<UserAccount> findAllByAgencyIdAndRoleAndStatus(UUID agencyId, String role, String status);

    Optional<UserAccount> findByInvitationToken(String invitationToken);

    Optional<UserAccount> findByPasswordResetToken(String passwordResetToken);
}
