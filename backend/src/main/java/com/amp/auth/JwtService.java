package com.amp.auth;

import com.amp.config.SystemSettingService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Service responsible for generating and validating self-issued JWT tokens.
 * <p>
 * Uses HS256 (HMAC-SHA256) for signing.  Access tokens carry user claims
 * (userId, email, role, agencyId); refresh tokens carry only the userId.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpMs;
    private final long refreshTokenExpMs;
    private final SystemSettingService systemSettingService;

    // Defaults used as fallbacks when DB is unreachable
    private static final long DEFAULT_REMEMBER_ME_DAYS = 30;
    private static final long DEFAULT_NORMAL_HOURS = 24;

    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.access-token-exp-ms:3600000}") long accessTokenExpMs,
            @Value("${app.security.refresh-token-exp-ms:604800000}") long refreshTokenExpMs,
            SystemSettingService systemSettingService) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpMs = accessTokenExpMs;
        this.refreshTokenExpMs = refreshTokenExpMs;
        this.systemSettingService = systemSettingService;
    }

    // ── token generation ────────────────────────────────────────

    /**
     * Generate an access token containing user claims.
     */
    public String generateAccessToken(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .claim("agencyId", user.getAgencyId() != null ? user.getAgencyId().toString() : null)
                .claim("clientId", user.getClientId() != null ? user.getClientId().toString() : null)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpMs)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a refresh token (carries userId only).
     * Uses the default configured expiry.
     */
    public String generateRefreshToken(UserAccount user) {
        return generateRefreshToken(user, refreshTokenExpMs);
    }

    /**
     * Generate a refresh token with an explicit expiry duration.
     *
     * @param rememberMe if true, 30-day expiry; if false, 24-hour expiry
     */
    public String generateRefreshToken(UserAccount user, boolean rememberMe) {
        long rememberMeDays = systemSettingService.getLong(
                "security.refresh.token.expiry.days.remember", DEFAULT_REMEMBER_ME_DAYS);
        long normalHours = systemSettingService.getLong(
                "security.refresh.token.expiry.hours.normal", DEFAULT_NORMAL_HOURS);
        long rememberMeExp = rememberMeDays * 24 * 60 * 60 * 1000;
        long shortExp = normalHours * 60 * 60 * 1000;
        return generateRefreshToken(user, rememberMe ? rememberMeExp : shortExp);
    }

    /**
     * Generate a refresh token with a custom expiry in milliseconds.
     */
    public String generateRefreshToken(UserAccount user, long expiryMs) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiryMs)))
                .signWith(signingKey)
                .compact();
    }

    // ── token parsing / validation ──────────────────────────────

    /**
     * Parse and validate a JWT, returning its claims.
     *
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extract the user-id (subject) from a valid token.
     */
    public UUID extractUserId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    /**
     * Check whether a token is valid (parseable and not expired).
     */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Return the claim "type" from a token ("access" or "refresh").
     */
    public String tokenType(String token) {
        return parse(token).get("type", String.class);
    }
}
