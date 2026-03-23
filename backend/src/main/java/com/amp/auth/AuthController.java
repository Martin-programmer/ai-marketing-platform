package com.amp.auth;

import com.amp.agency.Agency;
import com.amp.agency.AgencyRepository;
import com.amp.common.EmailService;
import com.amp.config.SystemSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication endpoints — login, refresh, register, me, 2FA.
 * <p>
 * All paths under {@code /api/v1/auth/**} are permitted in
 * {@link SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserAccountRepository userAccountRepository;
    private final AgencyRepository agencyRepository;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final SystemSettingService systemSettingService;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int TWO_FACTOR_CODE_LENGTH = 6;
    private static final int TWO_FACTOR_CODE_EXPIRY_MINUTES = 10;
    private static final int TWO_FACTOR_MAX_ATTEMPTS = 5;
    private static final int TWO_FACTOR_LOCKOUT_MINUTES = 15;
    private static final int TWO_FACTOR_MAX_RESENDS = 3;
    private static final int TWO_FACTOR_RESEND_COOLDOWN_SECONDS = 60;
    private static final int LOGIN_MAX_ATTEMPTS_PER_EMAIL = 5;
    private static final int LOGIN_LOCKOUT_MINUTES = 15;
    private static final int LOGIN_MAX_ATTEMPTS_PER_IP_HOUR = 20;

    public AuthController(UserAccountRepository userAccountRepository,
                          AgencyRepository agencyRepository,
                          JwtService jwtService,
                          EmailService emailService,
                          SystemSettingService systemSettingService,
                          StringRedisTemplate redisTemplate) {
        this.userAccountRepository = userAccountRepository;
        this.agencyRepository = agencyRepository;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.systemSettingService = systemSettingService;
        this.redisTemplate = redisTemplate;
    }

    // ── DTOs ────────────────────────────────────────────────────

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            boolean rememberMe) {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6) String password,
            @NotBlank String displayName,
            String role,
            UUID agencyId) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserInfo user) {}

    public record TwoFactorRequiredResponse(
            boolean requiresTwoFactor,
            String email,
            int resendCooldownSeconds) {}

    public record RefreshRequest(
            @NotBlank String refreshToken) {}

    public record Verify2faRequest(
            @NotBlank @Email String email,
            @NotBlank String code,
            boolean rememberMe) {}

    public record Resend2faRequest(
            @NotBlank @Email String email) {}

    public record UserInfo(
            UUID id,
            String email,
            String displayName,
            String role,
            UUID agencyId,
            UUID clientId) {}

    // ── endpoints ───────────────────────────────────────────────

    /**
     * POST /api/v1/auth/login – authenticate by email + password.
     * <p>
     * If the user's agency (or user) has 2FA enabled, a verification code is
     * emailed and a {@link TwoFactorRequiredResponse} is returned instead of tokens.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        if (isIpRateLimited(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("code", "IP_RATE_LIMIT",
                            "message", "Too many attempts. Please try again later."));
        }

        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = userAccountRepository.findByEmail(req.email()).orElse(null);

        if (user != null && user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now)) {
                long remainingMinutes = remainingMinutes(now, user.getLockedUntil());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("code", "ACCOUNT_LOCKED",
                                "message", "Account locked. Try again in " + remainingMinutes + " minutes.",
                                "remainingMinutes", remainingMinutes));
            }
            clearLoginLock(user);
            userAccountRepository.save(user);
        }

        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            incrementIpFailureCount(clientIp);
            return handleFailedLogin(user, now);
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "ACCOUNT_INACTIVE", "message", "Account is not active"));
        }

        resetLoginFailureState(user);

        // Check if 2FA is required
        if (isTwoFactorRequired(user)) {
            // Check lockout
            if (user.getTwoFactorLockedUntil() != null
                    && user.getTwoFactorLockedUntil().isAfter(OffsetDateTime.now())) {
                long remainingMinutes = remainingMinutes(OffsetDateTime.now(), user.getTwoFactorLockedUntil());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("code", "ACCOUNT_LOCKED",
                        "message", "Account locked. Try again in " + remainingMinutes + " minutes.",
                        "remainingMinutes", remainingMinutes));
            }

            return initiate2fa(user);
        }

        return ResponseEntity.ok(buildTokenResponse(user, req.rememberMe()));
    }

    /**
     * POST /api/v1/auth/verify-2fa – verify a 6-digit code after login.
     */
    @PostMapping("/verify-2fa")
    public ResponseEntity<?> verify2fa(@Valid @RequestBody Verify2faRequest req) {
        UserAccount user = userAccountRepository.findByEmail(req.email()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "INVALID_CREDENTIALS", "message", "Invalid email"));
        }

        // Check lockout
        if (user.getTwoFactorLockedUntil() != null
                && user.getTwoFactorLockedUntil().isAfter(OffsetDateTime.now())) {
            long remainingMinutes = remainingMinutes(OffsetDateTime.now(), user.getTwoFactorLockedUntil());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("code", "ACCOUNT_LOCKED",
                    "message", "Account locked. Try again in " + remainingMinutes + " minutes.",
                    "remainingMinutes", remainingMinutes));
        }

        // Check code exists
        if (user.getTwoFactorCode() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "NO_CODE", "message", "No verification code pending. Please login again."));
        }

        // Check expired
        if (user.getTwoFactorCodeExpiresAt() == null
                || user.getTwoFactorCodeExpiresAt().isBefore(OffsetDateTime.now())) {
            clearTwoFactorState(user);
            userAccountRepository.save(user);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "CODE_EXPIRED", "message", "Code expired. Please login again."));
        }

        // Check attempts
        int maxAttempts = systemSettingService.getInt("security.2fa.max.attempts", TWO_FACTOR_MAX_ATTEMPTS);
        int lockoutMinutes = systemSettingService.getInt("security.2fa.lockout.minutes", TWO_FACTOR_LOCKOUT_MINUTES);
        if (user.getTwoFactorAttempts() >= maxAttempts) {
            user.setTwoFactorLockedUntil(OffsetDateTime.now().plusMinutes(lockoutMinutes));
            clearTwoFactorState(user);
            userAccountRepository.save(user);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("code", "TOO_MANY_ATTEMPTS",
                        "message", "Account locked. Try again in " + lockoutMinutes + " minutes.",
                        "remainingMinutes", (long) lockoutMinutes));
        }

        // Validate code
        if (!req.code().equals(user.getTwoFactorCode())) {
            user.setTwoFactorAttempts(user.getTwoFactorAttempts() + 1);
            userAccountRepository.save(user);
            int remaining = maxAttempts - user.getTwoFactorAttempts();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "INVALID_CODE",
                            "message", "Invalid verification code",
                            "remainingAttempts", remaining));
        }

        // Success — clear 2FA state and issue tokens
        clearTwoFactorState(user);
        user.setTwoFactorLockedUntil(null);
        userAccountRepository.save(user);

        return ResponseEntity.ok(buildTokenResponse(user, req.rememberMe()));
    }

    /**
     * POST /api/v1/auth/resend-2fa – resend a fresh 6-digit verification code.
     */
    @PostMapping("/resend-2fa")
    public ResponseEntity<?> resend2fa(@Valid @RequestBody Resend2faRequest req) {
        UserAccount user = userAccountRepository.findByEmail(req.email()).orElse(null);
        if (user == null) {
            // Don't leak whether the email exists
            return ResponseEntity.ok(Map.of("message", "If the email is valid, a new code has been sent."));
        }

        // Must have a pending 2FA session
        if (user.getTwoFactorCode() == null || user.getTwoFactorCodeExpiresAt() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "NO_PENDING_2FA",
                            "message", "No pending verification. Please login again."));
        }

        // Rate limit resends
        int maxResends = systemSettingService.getInt("security.2fa.max.resends", TWO_FACTOR_MAX_RESENDS);
        if (user.getTwoFactorResendCount() >= maxResends) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("code", "RESEND_LIMIT",
                            "message", "Maximum resend attempts reached. Please login again."));
        }

        // Cooldown check — code must be at least COOLDOWN seconds old
        int expiryMinutes = systemSettingService.getInt("security.2fa.code.expiry.minutes", TWO_FACTOR_CODE_EXPIRY_MINUTES);
        int resendCooldown = systemSettingService.getInt("security.2fa.resend.cooldown.seconds", TWO_FACTOR_RESEND_COOLDOWN_SECONDS);
        OffsetDateTime earliestResend = user.getTwoFactorCodeExpiresAt()
                .minusMinutes(expiryMinutes)
                .plusSeconds(resendCooldown);
        if (OffsetDateTime.now().isBefore(earliestResend)) {
            long remainingSeconds = remainingSeconds(OffsetDateTime.now(), earliestResend);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("code", "RESEND_COOLDOWN",
                    "message", "Please wait " + remainingSeconds + " seconds before requesting a new code.",
                    "remainingSeconds", remainingSeconds));
        }

        // Generate new code
        String code = generateCode();
        user.setTwoFactorCode(code);
        user.setTwoFactorCodeExpiresAt(OffsetDateTime.now().plusMinutes(expiryMinutes));
        user.setTwoFactorAttempts(0);
        user.setTwoFactorResendCount(user.getTwoFactorResendCount() + 1);
        userAccountRepository.save(user);

        send2faEmail(user.getEmail(), code);

        return ResponseEntity.ok(Map.of(
            "message", "A new verification code has been sent.",
            "resendCooldownSeconds", resendCooldown
        ));
    }

    /**
     * POST /api/v1/auth/refresh – exchange a valid refresh token for new tokens.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req) {
        if (!jwtService.isValid(req.refreshToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "INVALID_REFRESH_TOKEN", "message", "Invalid or expired refresh token"));
        }

        String type = jwtService.tokenType(req.refreshToken());
        if (!"refresh".equals(type)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "INVALID_REFRESH_TOKEN", "message", "Token is not a refresh token"));
        }

        UUID userId = jwtService.extractUserId(req.refreshToken());
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "USER_NOT_FOUND", "message", "User not found"));
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "ACCOUNT_INACTIVE", "message", "Account is not active"));
        }

        return ResponseEntity.ok(buildTokenResponse(user, false));
    }

    /**
     * POST /api/v1/auth/register – create a new user account.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userAccountRepository.findByEmail(req.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "EMAIL_EXISTS", "message", "Email already registered"));
        }

        UserAccount user = new UserAccount();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName());
        user.setRole(req.role() != null ? req.role() : "AGENCY_ADMIN");
        user.setAgencyId(req.agencyId());
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        // cognito_sub is NOT NULL — use placeholder for self-issued auth
        user.setCognitoSub("local-" + UUID.randomUUID());

        user = userAccountRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(buildTokenResponse(user, false));
    }

    /**
     * GET /api/v1/auth/me – return the current user's info from the JWT.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        UUID userId = jwtService.extractUserId(token);

        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", "USER_NOT_FOUND", "message", "User not found"));
        }

        return ResponseEntity.ok(new UserInfo(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getAgencyId(),
                user.getClientId()));
    }

    // ── helpers ─────────────────────────────────────────────────

    /**
     * Check if 2FA is required for this user — either their agency enforces it
     * or the user has individually enabled it.
     */
    private boolean isTwoFactorRequired(UserAccount user) {
        if (user.isTwoFactorEnabled()) {
            return true;
        }
        if (user.getAgencyId() != null) {
            Agency agency = agencyRepository.findById(user.getAgencyId()).orElse(null);
            if (agency != null && agency.isTwoFactorEnabled()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Initiate 2FA: generate code, save, send email, return masked email.
     */
    private ResponseEntity<?> initiate2fa(UserAccount user) {
        String code = generateCode();
        int expiryMinutes = systemSettingService.getInt("security.2fa.code.expiry.minutes", TWO_FACTOR_CODE_EXPIRY_MINUTES);
        int resendCooldown = systemSettingService.getInt("security.2fa.resend.cooldown.seconds", TWO_FACTOR_RESEND_COOLDOWN_SECONDS);
        user.setTwoFactorCode(code);
        user.setTwoFactorCodeExpiresAt(OffsetDateTime.now().plusMinutes(expiryMinutes));
        user.setTwoFactorAttempts(0);
        user.setTwoFactorResendCount(0);
        userAccountRepository.save(user);

        send2faEmail(user.getEmail(), code);

        String maskedEmail = maskEmail(user.getEmail());
        log.info("2FA code sent to {} (masked: {})", user.getEmail(), maskedEmail);

        return ResponseEntity.ok(new TwoFactorRequiredResponse(true, maskedEmail, resendCooldown));
    }

    private ResponseEntity<?> handleFailedLogin(UserAccount user, OffsetDateTime now) {
        if (user == null || user.getPasswordHash() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "INVALID_CREDENTIALS", "message", "Invalid email or password."));
        }

        int maxAttempts = systemSettingService.getInt(
                "security.login.max.attempts.per.email", LOGIN_MAX_ATTEMPTS_PER_EMAIL);
        int lockoutMinutes = systemSettingService.getInt(
                "security.login.lockout.minutes", LOGIN_LOCKOUT_MINUTES);

        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= maxAttempts) {
            user.setLockedUntil(now.plusMinutes(lockoutMinutes));
            userAccountRepository.save(user);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("code", "ACCOUNT_LOCKED",
                            "message", "Account locked. Try again in " + lockoutMinutes + " minutes.",
                            "remainingMinutes", (long) lockoutMinutes));
        }

        userAccountRepository.save(user);
        int remainingAttempts = Math.max(maxAttempts - user.getFailedLoginAttempts(), 0);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", "INVALID_CREDENTIALS",
                        "message", "Invalid email or password.",
                        "remainingAttempts", remainingAttempts));
    }

    private boolean isIpRateLimited(String clientIp) {
        int maxAttempts = systemSettingService.getInt(
                "security.login.max.attempts.per.ip.hour", LOGIN_MAX_ATTEMPTS_PER_IP_HOUR);
        String raw = redisTemplate.opsForValue().get(loginIpKey(clientIp));
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            return Integer.parseInt(raw) >= maxAttempts;
        } catch (NumberFormatException e) {
            log.warn("Invalid login IP counter for {}", clientIp);
            redisTemplate.delete(loginIpKey(clientIp));
            return false;
        }
    }

    private void incrementIpFailureCount(String clientIp) {
        String key = loginIpKey(clientIp);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofHours(1));
        }
    }

    private String loginIpKey(String clientIp) {
        return "login_ip:" + clientIp;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void clearLoginLock(UserAccount user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }

    private void resetLoginFailureState(UserAccount user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            clearLoginLock(user);
            userAccountRepository.save(user);
        }
    }

    private long remainingMinutes(OffsetDateTime from, OffsetDateTime until) {
        return Math.max(1L, (long) Math.ceil(Math.max(0, ChronoUnit.SECONDS.between(from, until)) / 60.0));
    }

    private long remainingSeconds(OffsetDateTime from, OffsetDateTime until) {
        return Math.max(1L, ChronoUnit.SECONDS.between(from, until));
    }

    private String generateCode() {
        int length = systemSettingService.getInt("security.2fa.code.length", TWO_FACTOR_CODE_LENGTH);
        int min = (int) Math.pow(10, length - 1);
        int range = (int) Math.pow(10, length) - min;
        int code = secureRandom.nextInt(range) + min;
        return String.valueOf(code);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return email;
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private void clearTwoFactorState(UserAccount user) {
        user.setTwoFactorCode(null);
        user.setTwoFactorCodeExpiresAt(null);
        user.setTwoFactorAttempts(0);
        user.setTwoFactorResendCount(0);
    }

    private void send2faEmail(String to, String code) {
        try {
            emailService.sendTemplatedEmail(to, "Your verification code", "two-factor-code",
                    Map.of("code", code));
        } catch (Exception e) {
            log.error("Failed to send 2FA email to {}: {}", to, e.getMessage());
        }
    }

    private TokenResponse buildTokenResponse(UserAccount user, boolean rememberMe) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user, rememberMe);
        UserInfo info = new UserInfo(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.getAgencyId(), user.getClientId());
        return new TokenResponse(accessToken, refreshToken, 3600, info);
    }
}
