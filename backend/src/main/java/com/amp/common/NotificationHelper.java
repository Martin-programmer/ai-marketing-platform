package com.amp.common;

import com.amp.auth.UserAccount;
import com.amp.auth.UserAccountRepository;
import com.amp.clients.UserClientPermission;
import com.amp.clients.UserClientPermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Helper for finding notification recipients and sending fire-and-forget emails.
 * <p>
 * All email sending is async via {@link CompletableFuture#runAsync(Runnable)} so
 * it never blocks the caller. Failures are caught and logged.
 */
@Component
public class NotificationHelper {

    private static final Logger log = LoggerFactory.getLogger(NotificationHelper.class);

    private final UserAccountRepository userAccountRepo;
    private final UserClientPermissionRepository permissionRepo;
    private final EmailService emailService;

    public NotificationHelper(UserAccountRepository userAccountRepo,
                              UserClientPermissionRepository permissionRepo,
                              EmailService emailService) {
        this.userAccountRepo = userAccountRepo;
        this.permissionRepo = permissionRepo;
        this.emailService = emailService;
    }

    // ──────── Recipient resolution ────────

    /**
     * Returns emails of all ACTIVE AGENCY_ADMIN users for the given agency.
     */
    public List<String> getAgencyAdminEmails(UUID agencyId) {
        return userAccountRepo.findAllByAgencyIdAndRoleAndStatus(agencyId, "AGENCY_ADMIN", "ACTIVE")
                .stream()
                .map(UserAccount::getEmail)
                .toList();
    }

    /**
     * Returns emails of all ACTIVE CLIENT_USER users for the given client.
     */
    public List<String> getClientUserEmails(UUID clientId) {
        return userAccountRepo.findAllByClientIdAndStatus(clientId, "ACTIVE")
                .stream()
                .filter(u -> "CLIENT_USER".equals(u.getRole()))
                .map(UserAccount::getEmail)
                .toList();
    }

    /**
     * Returns emails of AGENCY_ADMIN + AGENCY_USERs with explicit permission for the client.
     * This is the set of people who should be notified about campaign / anomaly events.
     */
    public List<String> getAssignedUserEmails(UUID agencyId, UUID clientId) {
        Set<String> emails = new HashSet<>();

        // All active agency admins for this agency
        emails.addAll(getAgencyAdminEmails(agencyId));

        // Agency users who have a permission entry for this client
        List<UserClientPermission> permissions = permissionRepo.findAllByClientId(clientId);
        List<UUID> assignedUserIds = permissions.stream()
                .map(UserClientPermission::getUserId)
                .distinct()
                .toList();

        for (UUID userId : assignedUserIds) {
            userAccountRepo.findById(userId)
                    .filter(u -> "ACTIVE".equals(u.getStatus()))
                    .filter(u -> "AGENCY_USER".equals(u.getRole()))
                    .map(UserAccount::getEmail)
                    .ifPresent(emails::add);
        }

        return new ArrayList<>(emails);
    }

    // ──────── Async email dispatch ────────

    /**
     * Send an email asynchronously (fire-and-forget). Exceptions are caught and logged.
     */
    public void sendAsync(String to, String subject, String htmlBody) {
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendEmail(to, subject, htmlBody);
            } catch (Exception e) {
                log.error("Async email failed to={} subject=\"{}\": {}", to, subject, e.getMessage(), e);
            }
        });
    }

    /**
     * Send a templated email asynchronously (fire-and-forget).
     */
    public void sendTemplatedAsync(String to, String subject, String templateName,
                                   java.util.Map<String, String> variables) {
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendTemplatedEmail(to, subject, templateName, variables);
            } catch (Exception e) {
                log.error("Async email failed to={} subject=\"{}\": {}", to, subject, e.getMessage(), e);
            }
        });
    }

    /**
     * Send an email to multiple recipients asynchronously.
     */
    public void sendToAllAsync(List<String> recipients, String subject, String htmlBody) {
        for (String to : recipients) {
            sendAsync(to, subject, htmlBody);
        }
    }

    /**
     * Send a raw HTML email (already fully composed, without the EmailService wrapper template).
     */
    public void sendRawAsync(String to, String subject, String fullHtml) {
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendEmail(to, subject, fullHtml);
            } catch (Exception e) {
                log.error("Async raw email failed to={} subject=\"{}\": {}", to, subject, e.getMessage(), e);
            }
        });
    }
}
