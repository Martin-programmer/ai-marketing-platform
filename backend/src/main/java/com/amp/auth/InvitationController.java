package com.amp.auth;

import com.amp.common.EmailProperties;
import com.amp.common.NotificationHelper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Public endpoints for invitation acceptance, password reset, and forgot-password.
 * <p>
 * All paths under {@code /api/v1/auth/**} are permitted in {@link SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class InvitationController {

    private static final Logger log = LoggerFactory.getLogger(InvitationController.class);

    private final UserAccountRepository userAccountRepository;
    private final JwtService jwtService;
    private final UserService userService;
    private final NotificationHelper notificationHelper;
    private final EmailProperties emailProperties;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public InvitationController(UserAccountRepository userAccountRepository,
                                JwtService jwtService,
                                UserService userService,
                                NotificationHelper notificationHelper,
                                EmailProperties emailProperties) {
        this.userAccountRepository = userAccountRepository;
        this.jwtService = jwtService;
        this.userService = userService;
        this.notificationHelper = notificationHelper;
        this.emailProperties = emailProperties;
    }

    // ── DTOs ────────────────────────────────────────────────────

    public record AcceptInviteRequest(
            @NotBlank String token,
            @NotBlank String displayName,
            @NotBlank @Size(min = 6) String password) {}

    public record InviteInfoResponse(
            String email,
            String role,
            String agencyName,
            boolean expired) {}

    public record ForgotPasswordRequest(
            @NotBlank @Email String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 6) String newPassword) {}

    // ── Invite Info ─────────────────────────────────────────────

    /**
     * GET /api/v1/auth/invite-info?token=xxx
     * Returns info about the invitation (email, role, agency, expired status).
     */
    @GetMapping("/invite-info")
    public ResponseEntity<?> getInviteInfo(@RequestParam String token) {
        Optional<UserAccount> opt = userAccountRepository.findByInvitationToken(token);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", "INVALID_TOKEN", "message", "Invalid or already used invitation token"));
        }

        UserAccount user = opt.get();
        boolean expired = user.getInvitationExpiresAt() != null
                && user.getInvitationExpiresAt().isBefore(OffsetDateTime.now());

        // Resolve agency name
        String agencyName = userService.resolveAgencyName(user.getAgencyId());

        return ResponseEntity.ok(new InviteInfoResponse(
                user.getEmail(),
                user.getRole(),
                agencyName,
                expired
        ));
    }

    // ── Accept Invite ───────────────────────────────────────────

    /**
     * POST /api/v1/auth/accept-invite
     * Validates token, sets password, activates account, returns JWT (auto-login).
     */
    @PostMapping("/accept-invite")
    public ResponseEntity<?> acceptInvite(@Valid @RequestBody AcceptInviteRequest req) {
        Optional<UserAccount> opt = userAccountRepository.findByInvitationToken(req.token());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "INVALID_TOKEN", "message", "Invalid or already used invitation token"));
        }

        UserAccount user = opt.get();

        // Check expiry
        if (user.getInvitationExpiresAt() != null
                && user.getInvitationExpiresAt().isBefore(OffsetDateTime.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "TOKEN_EXPIRED", "message", "Invitation has expired. Please ask your admin to resend."));
        }

        // Activate the account
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName());
        user.setStatus("ACTIVE");
        user.setInvitationToken(null);
        user.setInvitationExpiresAt(null);
        user.setUpdatedAt(OffsetDateTime.now());
        user = userAccountRepository.save(user);

        log.info("User {} accepted invitation and activated account", user.getEmail());

        // Send welcome email (fire-and-forget)
        try {
            sendWelcomeEmail(user);
        } catch (Exception e) {
            log.warn("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }

        // Build JWT response (auto-login)
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        var userInfo = new AuthController.UserInfo(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.getAgencyId(), user.getClientId());

        return ResponseEntity.ok(new AuthController.TokenResponse(accessToken, refreshToken, 3600, userInfo));
    }

    // ── Forgot Password ─────────────────────────────────────────

    /**
     * POST /api/v1/auth/forgot-password
     * Generates reset token, sends email. Always returns 200 (don't reveal if email exists).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        log.info("Forgot password requested for email: {}", req.email());

        Optional<UserAccount> opt = userAccountRepository.findByEmail(req.email());
        if (opt.isPresent()) {
            UserAccount user = opt.get();
            if ("ACTIVE".equals(user.getStatus())) {
                userService.generateAndSendPasswordReset(user);
            }
        }

        // Always return 200 — don't reveal if email exists
        return ResponseEntity.ok(Map.of(
                "message", "If this email is registered, you will receive a password reset link."
        ));
    }

    // ── Reset Password ──────────────────────────────────────────

    /**
     * POST /api/v1/auth/reset-password
     * Validates token, updates password, clears token.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        Optional<UserAccount> opt = userAccountRepository.findByPasswordResetToken(req.token());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "INVALID_TOKEN", "message", "Invalid or already used reset token"));
        }

        UserAccount user = opt.get();

        // Check expiry
        if (user.getPasswordResetExpiresAt() != null
                && user.getPasswordResetExpiresAt().isBefore(OffsetDateTime.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "TOKEN_EXPIRED", "message", "Reset token has expired. Please request a new one."));
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        user.setUpdatedAt(OffsetDateTime.now());
        userAccountRepository.save(user);

        log.info("User {} successfully reset their password", user.getEmail());

        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully. You can now log in."));
    }

    // ── Welcome Email ───────────────────────────────────────────

    private void sendWelcomeEmail(UserAccount user) {
        String displayName = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
        String agencyName = userService.resolveAgencyName(user.getAgencyId());
        String loginLink = emailProperties.getBaseUrl() + "/login";

        String welcomeMessage = switch (user.getRole()) {
            case "AGENCY_ADMIN" -> "Welcome! You can now manage your agency <strong>" + agencyName
                    + "</strong> on AI Marketing Platform. Set up your clients, connect Meta accounts, and start optimizing.";
            case "AGENCY_USER" -> "Welcome! You've been added to <strong>" + agencyName
                    + "</strong>. You can now access the clients assigned to you and help manage their campaigns.";
            case "CLIENT_USER" -> "Welcome! You now have access to your performance portal. "
                    + "View your reports, track KPIs, and see AI-powered insights about your campaigns.";
            default -> "Welcome to AI Marketing Platform! Your account is now active.";
        };

        notificationHelper.sendTemplatedAsync(user.getEmail(),
                "Welcome to AI Marketing Platform!",
                "welcome",
                Map.of(
                        "displayName", displayName,
                        "welcomeMessage", welcomeMessage,
                        "loginLink", loginLink
                ));

        log.info("Queued welcome email to {} (role: {})", user.getEmail(), user.getRole());
    }
}
