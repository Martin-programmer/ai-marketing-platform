package com.amp.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication endpoints — login, refresh, register, me.
 * <p>
 * All paths under {@code /api/v1/auth/**} are permitted in
 * {@link SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserAccountRepository userAccountRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserAccountRepository userAccountRepository, JwtService jwtService) {
        this.userAccountRepository = userAccountRepository;
        this.jwtService = jwtService;
    }

    // ── DTOs ────────────────────────────────────────────────────

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

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

    public record RefreshRequest(
            @NotBlank String refreshToken) {}

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
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        UserAccount user = userAccountRepository.findByEmail(req.email()).orElse(null);

        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "INVALID_CREDENTIALS", "message", "Invalid email or password"));
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "ACCOUNT_INACTIVE", "message", "Account is not active"));
        }

        return ResponseEntity.ok(buildTokenResponse(user));
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

        return ResponseEntity.ok(buildTokenResponse(user));
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

        return ResponseEntity.status(HttpStatus.CREATED).body(buildTokenResponse(user));
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

    private TokenResponse buildTokenResponse(UserAccount user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        UserInfo info = new UserInfo(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.getAgencyId(), user.getClientId());
        // expiresIn is in seconds for the client
        return new TokenResponse(accessToken, refreshToken, 3600, info);
    }
}
