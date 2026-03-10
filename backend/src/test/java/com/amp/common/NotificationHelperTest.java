package com.amp.common;

import com.amp.auth.UserAccount;
import com.amp.auth.UserAccountRepository;
import com.amp.clients.UserClientPermission;
import com.amp.clients.UserClientPermissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationHelper}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationHelper — Recipient Resolution & Async Email")
class NotificationHelperTest {

    private static final UUID AGENCY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Mock private UserAccountRepository userAccountRepo;
    @Mock private UserClientPermissionRepository permissionRepo;
    @Mock private EmailService emailService;

    @InjectMocks
    private NotificationHelper notificationHelper;

    // ──────── helpers ────────

    private UserAccount buildUser(String email, String role, String status) {
        UserAccount u = new UserAccount();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setRole(role);
        u.setStatus(status);
        u.setAgencyId(AGENCY_ID);
        return u;
    }

    // ──────── getAgencyAdminEmails ────────

    @Test
    @DisplayName("getAgencyAdminEmails — returns only ACTIVE AGENCY_ADMIN emails")
    void getAgencyAdminEmails_returnsActive() {
        UserAccount admin1 = buildUser("admin1@agency.com", "AGENCY_ADMIN", "ACTIVE");
        UserAccount admin2 = buildUser("admin2@agency.com", "AGENCY_ADMIN", "ACTIVE");

        when(userAccountRepo.findAllByAgencyIdAndRoleAndStatus(AGENCY_ID, "AGENCY_ADMIN", "ACTIVE"))
                .thenReturn(List.of(admin1, admin2));

        List<String> emails = notificationHelper.getAgencyAdminEmails(AGENCY_ID);

        assertThat(emails).containsExactlyInAnyOrder("admin1@agency.com", "admin2@agency.com");
    }

    @Test
    @DisplayName("getAgencyAdminEmails — returns empty when no admins")
    void getAgencyAdminEmails_empty() {
        when(userAccountRepo.findAllByAgencyIdAndRoleAndStatus(AGENCY_ID, "AGENCY_ADMIN", "ACTIVE"))
                .thenReturn(List.of());

        List<String> emails = notificationHelper.getAgencyAdminEmails(AGENCY_ID);

        assertThat(emails).isEmpty();
    }

    // ──────── getClientUserEmails ────────

    @Test
    @DisplayName("getClientUserEmails — returns only ACTIVE CLIENT_USERs")
    void getClientUserEmails_returnsActive() {
        UserAccount client1 = buildUser("client1@example.com", "CLIENT_USER", "ACTIVE");
        UserAccount invited = buildUser("invited@example.com", "CLIENT_USER", "INVITED");

        when(userAccountRepo.findAllByClientIdAndStatus(CLIENT_ID, "ACTIVE"))
                .thenReturn(List.of(client1)); // invited is filtered by repo query

        List<String> emails = notificationHelper.getClientUserEmails(CLIENT_ID);

        assertThat(emails).containsExactly("client1@example.com");
    }

    // ──────── getAssignedUserEmails ────────

    @Test
    @DisplayName("getAssignedUserEmails — returns admins + assigned agency users")
    void getAssignedUserEmails_combined() {
        UserAccount admin = buildUser("admin@agency.com", "AGENCY_ADMIN", "ACTIVE");
        UserAccount agencyUser = buildUser("user@agency.com", "AGENCY_USER", "ACTIVE");
        agencyUser.setId(UUID.fromString("00000000-0000-0000-0000-000000000011"));

        // Admins
        when(userAccountRepo.findAllByAgencyIdAndRoleAndStatus(AGENCY_ID, "AGENCY_ADMIN", "ACTIVE"))
                .thenReturn(List.of(admin));

        // Assigned users via permissions
        UserClientPermission perm = new UserClientPermission();
        perm.setUserId(agencyUser.getId());
        perm.setClientId(CLIENT_ID);
        when(permissionRepo.findAllByClientId(CLIENT_ID)).thenReturn(List.of(perm));
        when(userAccountRepo.findById(agencyUser.getId())).thenReturn(Optional.of(agencyUser));

        List<String> emails = notificationHelper.getAssignedUserEmails(AGENCY_ID, CLIENT_ID);

        assertThat(emails).containsExactlyInAnyOrder("admin@agency.com", "user@agency.com");
    }

    @Test
    @DisplayName("getAssignedUserEmails — excludes non-ACTIVE assigned users")
    void getAssignedUserEmails_excludesInactive() {
        UserAccount admin = buildUser("admin@agency.com", "AGENCY_ADMIN", "ACTIVE");
        UserAccount inactiveUser = buildUser("inactive@agency.com", "AGENCY_USER", "DISABLED");
        inactiveUser.setId(UUID.fromString("00000000-0000-0000-0000-000000000012"));

        when(userAccountRepo.findAllByAgencyIdAndRoleAndStatus(AGENCY_ID, "AGENCY_ADMIN", "ACTIVE"))
                .thenReturn(List.of(admin));

        UserClientPermission perm = new UserClientPermission();
        perm.setUserId(inactiveUser.getId());
        perm.setClientId(CLIENT_ID);
        when(permissionRepo.findAllByClientId(CLIENT_ID)).thenReturn(List.of(perm));
        when(userAccountRepo.findById(inactiveUser.getId())).thenReturn(Optional.of(inactiveUser));

        List<String> emails = notificationHelper.getAssignedUserEmails(AGENCY_ID, CLIENT_ID);

        assertThat(emails).containsExactly("admin@agency.com");
    }

    // ──────── sendAsync ────────

    @Test
    @DisplayName("sendAsync — delegates to emailService and doesn't throw")
    void sendAsync_delegates() throws InterruptedException {
        notificationHelper.sendAsync("test@example.com", "Test Subject", "<p>body</p>");

        // Give the async task time to complete
        Thread.sleep(200);

        verify(emailService).sendEmail("test@example.com", "Test Subject", "<p>body</p>");
    }

    @Test
    @DisplayName("sendToAllAsync — sends to each recipient")
    void sendToAllAsync_sendsToEach() throws InterruptedException {
        notificationHelper.sendToAllAsync(
                List.of("a@test.com", "b@test.com"),
                "Subject", "<p>body</p>");

        Thread.sleep(200);

        verify(emailService).sendEmail("a@test.com", "Subject", "<p>body</p>");
        verify(emailService).sendEmail("b@test.com", "Subject", "<p>body</p>");
    }

    @Test
    @DisplayName("sendAsync — swallows exception from emailService")
    void sendAsync_swallowsException() throws InterruptedException {
        doThrow(new RuntimeException("SES down")).when(emailService)
                .sendEmail(any(), any(), any());

        // Should NOT throw
        notificationHelper.sendAsync("test@example.com", "Subject", "<p>body</p>");

        Thread.sleep(200);

        verify(emailService).sendEmail("test@example.com", "Subject", "<p>body</p>");
    }
}
