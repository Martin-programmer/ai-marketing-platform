package com.amp.auth;

import com.amp.agency.Agency;
import com.amp.agency.AgencyRepository;
import com.amp.common.EmailProperties;
import com.amp.common.EmailService;
import com.amp.common.NotificationHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InvitationController}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationController — Invite & Password Reset Tests")
class InvitationControllerTest {

    private static final UUID AGENCY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private JwtService jwtService;
    @Mock private UserService userService;
    @Mock private NotificationHelper notificationHelper;
    @Mock private EmailProperties emailProperties;

    @InjectMocks
    private InvitationController controller;

    // ──────── helpers ────────

    private UserAccount buildInvitedUser(String token, OffsetDateTime expiresAt) {
        UserAccount u = new UserAccount();
        u.setId(USER_ID);
        u.setEmail("invited@example.com");
        u.setRole("AGENCY_USER");
        u.setAgencyId(AGENCY_ID);
        u.setStatus("INVITED");
        u.setInvitationToken(token);
        u.setInvitationExpiresAt(expiresAt);
        u.setCognitoSub("local-" + UUID.randomUUID());
        u.setCreatedAt(OffsetDateTime.now());
        u.setUpdatedAt(OffsetDateTime.now());
        return u;
    }

    private UserAccount buildActiveUser(String resetToken, OffsetDateTime resetExpiresAt) {
        UserAccount u = new UserAccount();
        u.setId(USER_ID);
        u.setEmail("active@example.com");
        u.setRole("AGENCY_ADMIN");
        u.setAgencyId(AGENCY_ID);
        u.setStatus("ACTIVE");
        u.setPasswordHash("$2a$hashed");
        u.setPasswordResetToken(resetToken);
        u.setPasswordResetExpiresAt(resetExpiresAt);
        u.setCognitoSub("local-" + UUID.randomUUID());
        u.setCreatedAt(OffsetDateTime.now());
        u.setUpdatedAt(OffsetDateTime.now());
        return u;
    }

    // ──────── getInviteInfo ────────

    @Test
    @DisplayName("getInviteInfo — valid token: returns email, role, agency")
    void getInviteInfo_valid() {
        String token = UUID.randomUUID().toString();
        UserAccount user = buildInvitedUser(token, OffsetDateTime.now().plusHours(72));

        when(userAccountRepository.findByInvitationToken(token)).thenReturn(Optional.of(user));
        when(userService.resolveAgencyName(AGENCY_ID)).thenReturn("Test Agency");

        ResponseEntity<?> response = controller.getInviteInfo(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        InvitationController.InviteInfoResponse body =
                (InvitationController.InviteInfoResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.email()).isEqualTo("invited@example.com");
        assertThat(body.role()).isEqualTo("AGENCY_USER");
        assertThat(body.agencyName()).isEqualTo("Test Agency");
        assertThat(body.expired()).isFalse();
    }

    @Test
    @DisplayName("getInviteInfo — expired token: returns expired=true")
    void getInviteInfo_expired() {
        String token = UUID.randomUUID().toString();
        UserAccount user = buildInvitedUser(token, OffsetDateTime.now().minusHours(1));

        when(userAccountRepository.findByInvitationToken(token)).thenReturn(Optional.of(user));
        when(userService.resolveAgencyName(AGENCY_ID)).thenReturn("Test Agency");

        ResponseEntity<?> response = controller.getInviteInfo(token);

        InvitationController.InviteInfoResponse body =
                (InvitationController.InviteInfoResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.expired()).isTrue();
    }

    @Test
    @DisplayName("getInviteInfo — invalid token: 404")
    void getInviteInfo_notFound() {
        when(userAccountRepository.findByInvitationToken("invalid-token")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getInviteInfo("invalid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ──────── acceptInvite ────────

    @Test
    @DisplayName("acceptInvite — success: activates user, returns JWT")
    void acceptInvite_success() {
        String token = UUID.randomUUID().toString();
        UserAccount user = buildInvitedUser(token, OffsetDateTime.now().plusHours(72));

        when(userAccountRepository.findByInvitationToken(token)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(UserAccount.class))).thenReturn("access-jwt");
        when(jwtService.generateRefreshToken(any(UserAccount.class))).thenReturn("refresh-jwt");

        var req = new InvitationController.AcceptInviteRequest(token, "My Name", "password123");
        ResponseEntity<?> response = controller.acceptInvite(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify user was activated
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getDisplayName()).isEqualTo("My Name");
        assertThat(user.getPasswordHash()).isNotNull().isNotEmpty();
        assertThat(user.getInvitationToken()).isNull();
        assertThat(user.getInvitationExpiresAt()).isNull();
    }

    @Test
    @DisplayName("acceptInvite — expired token: 400")
    void acceptInvite_expired() {
        String token = UUID.randomUUID().toString();
        UserAccount user = buildInvitedUser(token, OffsetDateTime.now().minusHours(1));

        when(userAccountRepository.findByInvitationToken(token)).thenReturn(Optional.of(user));

        var req = new InvitationController.AcceptInviteRequest(token, "My Name", "password123");
        ResponseEntity<?> response = controller.acceptInvite(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("acceptInvite — invalid token: 400")
    void acceptInvite_invalidToken() {
        when(userAccountRepository.findByInvitationToken("bad-token")).thenReturn(Optional.empty());

        var req = new InvitationController.AcceptInviteRequest("bad-token", "My Name", "password123");
        ResponseEntity<?> response = controller.acceptInvite(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ──────── forgotPassword ────────

    @Test
    @DisplayName("forgotPassword — existing user: always returns 200, triggers reset")
    void forgotPassword_existingUser() {
        UserAccount user = buildActiveUser(null, null);
        when(userAccountRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));
        doNothing().when(userService).generateAndSendPasswordReset(any(UserAccount.class));

        var req = new InvitationController.ForgotPasswordRequest("active@example.com");
        ResponseEntity<?> response = controller.forgotPassword(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).generateAndSendPasswordReset(user);
    }

    @Test
    @DisplayName("forgotPassword — non-existing email: still returns 200 (security)")
    void forgotPassword_nonExisting() {
        when(userAccountRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        var req = new InvitationController.ForgotPasswordRequest("unknown@example.com");
        ResponseEntity<?> response = controller.forgotPassword(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService, never()).generateAndSendPasswordReset(any());
    }

    // ──────── resetPassword ────────

    @Test
    @DisplayName("resetPassword — valid token: updates password, clears token")
    void resetPassword_success() {
        String token = UUID.randomUUID().toString();
        UserAccount user = buildActiveUser(token, OffsetDateTime.now().plusHours(1));

        when(userAccountRepository.findByPasswordResetToken(token)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new InvitationController.ResetPasswordRequest(token, "newPassword123");
        ResponseEntity<?> response = controller.resetPassword(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.getPasswordResetToken()).isNull();
        assertThat(user.getPasswordResetExpiresAt()).isNull();
        assertThat(user.getPasswordHash()).isNotEqualTo("$2a$hashed"); // Changed
    }

    @Test
    @DisplayName("resetPassword — expired token: 400")
    void resetPassword_expired() {
        String token = UUID.randomUUID().toString();
        UserAccount user = buildActiveUser(token, OffsetDateTime.now().minusMinutes(30));

        when(userAccountRepository.findByPasswordResetToken(token)).thenReturn(Optional.of(user));

        var req = new InvitationController.ResetPasswordRequest(token, "newPassword123");
        ResponseEntity<?> response = controller.resetPassword(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("resetPassword — invalid token: 400")
    void resetPassword_invalidToken() {
        when(userAccountRepository.findByPasswordResetToken("bad-token")).thenReturn(Optional.empty());

        var req = new InvitationController.ResetPasswordRequest("bad-token", "newPassword123");
        ResponseEntity<?> response = controller.resetPassword(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
