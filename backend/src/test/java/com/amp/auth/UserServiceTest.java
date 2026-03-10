package com.amp.auth;

import com.amp.agency.Agency;
import com.amp.agency.AgencyRepository;
import com.amp.common.EmailProperties;
import com.amp.common.EmailService;
import com.amp.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService} — invitation and user management.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — Invitation & User Management Tests")
class UserServiceTest {

    private static final UUID AGENCY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private AgencyRepository agencyRepository;
    @Mock private EmailService emailService;
    @Mock private EmailProperties emailProperties;

    @InjectMocks
    private UserService userService;

    // ──────── helpers ────────

    private UserAccount buildUser(String status) {
        UserAccount u = new UserAccount();
        u.setId(USER_ID);
        u.setEmail("test@example.com");
        u.setDisplayName("Test User");
        u.setRole("AGENCY_USER");
        u.setAgencyId(AGENCY_ID);
        u.setStatus(status);
        u.setCognitoSub("local-" + UUID.randomUUID());
        u.setCreatedAt(OffsetDateTime.now());
        u.setUpdatedAt(OffsetDateTime.now());
        return u;
    }

    // ──────── inviteUser ────────

    @Test
    @DisplayName("inviteUser — success: creates INVITED user, sends email, no password hash")
    void inviteUser_success() {
        InviteUserRequest req = new InviteUserRequest("new@example.com", "AGENCY_USER", null);

        when(userAccountRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(emailProperties.getBaseUrl()).thenReturn("https://adverion.xyz");

        Agency agency = new Agency();
        agency.setId(AGENCY_ID);
        agency.setName("Test Agency");
        when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));

        UserResponse result = userService.inviteUser(AGENCY_ID, req);

        assertThat(result.status()).isEqualTo("INVITED");
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.role()).isEqualTo("AGENCY_USER");

        // Verify user was saved with invitation token and no password
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        UserAccount saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getInvitationToken()).isNotNull().isNotBlank();
        assertThat(saved.getInvitationExpiresAt()).isNotNull();
        assertThat(saved.getInvitationExpiresAt()).isAfter(OffsetDateTime.now().plusHours(71));

        // Verify email was sent
        verify(emailService).sendTemplatedEmail(eq("new@example.com"), anyString(), eq("invitation"), any());
    }

    @Test
    @DisplayName("inviteUser — CLIENT_USER: requires clientId, sets on user")
    void inviteUser_clientUser() {
        InviteUserRequest req = new InviteUserRequest("client@example.com", "CLIENT_USER", CLIENT_ID);

        when(userAccountRepository.findByEmail("client@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(emailProperties.getBaseUrl()).thenReturn("https://adverion.xyz");

        Agency agency = new Agency();
        agency.setId(AGENCY_ID);
        agency.setName("Test Agency");
        when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));

        UserResponse result = userService.inviteUser(AGENCY_ID, req);

        assertThat(result.role()).isEqualTo("CLIENT_USER");

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("inviteUser — CLIENT_USER without clientId: throws")
    void inviteUser_clientUserNoClientId() {
        InviteUserRequest req = new InviteUserRequest("client@example.com", "CLIENT_USER", null);

        assertThatThrownBy(() -> userService.inviteUser(AGENCY_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientId");
    }

    @Test
    @DisplayName("inviteUser — invalid role: throws")
    void inviteUser_invalidRole() {
        InviteUserRequest req = new InviteUserRequest("test@example.com", "INVALID_ROLE", null);

        assertThatThrownBy(() -> userService.inviteUser(AGENCY_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role");
    }

    @Test
    @DisplayName("inviteUser — duplicate email: throws")
    void inviteUser_duplicateEmail() {
        InviteUserRequest req = new InviteUserRequest("existing@example.com", "AGENCY_USER", null);

        when(userAccountRepository.findByEmail("existing@example.com"))
                .thenReturn(Optional.of(buildUser("ACTIVE")));

        assertThatThrownBy(() -> userService.inviteUser(AGENCY_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already registered");
    }

    // ──────── generateAndSendPasswordReset ────────

    @Test
    @DisplayName("generateAndSendPasswordReset — sets token, expiry 1h, sends email")
    void generateAndSendPasswordReset_success() {
        UserAccount user = buildUser("ACTIVE");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(emailProperties.getBaseUrl()).thenReturn("https://adverion.xyz");

        userService.generateAndSendPasswordReset(user);

        assertThat(user.getPasswordResetToken()).isNotNull().isNotBlank();
        assertThat(user.getPasswordResetExpiresAt()).isNotNull();
        assertThat(user.getPasswordResetExpiresAt()).isBefore(OffsetDateTime.now().plusHours(2));
        assertThat(user.getPasswordResetExpiresAt()).isAfter(OffsetDateTime.now().plusMinutes(55));

        verify(userAccountRepository).save(user);
        verify(emailService).sendTemplatedEmail(eq("test@example.com"), anyString(), eq("password-reset"), any());
    }

    // ──────── resolveAgencyName ────────

    @Test
    @DisplayName("resolveAgencyName — found: returns name")
    void resolveAgencyName_found() {
        Agency agency = new Agency();
        agency.setName("My Agency");
        when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));

        String name = userService.resolveAgencyName(AGENCY_ID);

        assertThat(name).isEqualTo("My Agency");
    }

    @Test
    @DisplayName("resolveAgencyName — null ID: returns 'Platform'")
    void resolveAgencyName_nullId() {
        String name = userService.resolveAgencyName(null);
        assertThat(name).isEqualTo("Platform");
    }

    // ──────── listUsers ────────

    @Test
    @DisplayName("listUsers — returns all for agency")
    void listUsers_returnsAll() {
        UserAccount u1 = buildUser("ACTIVE");
        UserAccount u2 = buildUser("INVITED");
        u2.setId(UUID.randomUUID());
        u2.setEmail("other@example.com");

        when(userAccountRepository.findAllByAgencyId(AGENCY_ID)).thenReturn(List.of(u1, u2));

        List<UserResponse> result = userService.listUsers(AGENCY_ID);

        assertThat(result).hasSize(2);
    }

    // ──────── disableUser ────────

    @Test
    @DisplayName("disableUser — success: sets DISABLED")
    void disableUser_success() {
        UserAccount user = buildUser("ACTIVE");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID currentUserId = UUID.randomUUID(); // Different user
        UserResponse result = userService.disableUser(USER_ID, AGENCY_ID, currentUserId);

        assertThat(result.status()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("disableUser — self: throws")
    void disableUser_self() {
        assertThatThrownBy(() -> userService.disableUser(USER_ID, AGENCY_ID, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot disable your own account");
    }

    // ──────── enableUser ────────

    @Test
    @DisplayName("enableUser — success: DISABLED → ACTIVE")
    void enableUser_success() {
        UserAccount user = buildUser("DISABLED");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.enableUser(USER_ID, AGENCY_ID);

        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    // ──────── getUser ────────

    @Test
    @DisplayName("getUser — wrong agency: throws ResourceNotFoundException")
    void getUser_wrongAgency() {
        UserAccount user = buildUser("ACTIVE");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UUID otherAgency = UUID.randomUUID();
        assertThatThrownBy(() -> userService.getUser(USER_ID, otherAgency))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
