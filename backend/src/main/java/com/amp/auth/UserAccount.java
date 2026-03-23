package com.amp.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code user_account} table (V001 + V007 + V008).
 * <p>
 * {@code agency_id} is nullable — OWNER_ADMIN users have no agency.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = true)
    private UUID agencyId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "cognito_sub", nullable = false, unique = true)
    private String cognitoSub;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "invitation_token")
    private String invitationToken;

    @Column(name = "invitation_expires_at")
    private OffsetDateTime invitationExpiresAt;

    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "password_reset_expires_at")
    private OffsetDateTime passwordResetExpiresAt;

    @Column(name = "two_factor_enabled")
    private boolean twoFactorEnabled;

    @Column(name = "two_factor_code")
    private String twoFactorCode;

    @Column(name = "two_factor_code_expires_at")
    private OffsetDateTime twoFactorCodeExpiresAt;

    @Column(name = "two_factor_attempts")
    private int twoFactorAttempts;

    @Column(name = "two_factor_locked_until")
    private OffsetDateTime twoFactorLockedUntil;

    @Column(name = "two_factor_resend_count")
    private int twoFactorResendCount;

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_password_reset_request_at")
    private OffsetDateTime lastPasswordResetRequestAt;

    @Column(name = "password_reset_count_hourly")
    private int passwordResetCountHourly;

    @Column(name = "password_reset_count_reset_at")
    private OffsetDateTime passwordResetCountResetAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UserAccount() {}

    @PreUpdate
    private void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    // ---- getters & setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCognitoSub() { return cognitoSub; }
    public void setCognitoSub(String cognitoSub) { this.cognitoSub = cognitoSub; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getInvitationToken() { return invitationToken; }
    public void setInvitationToken(String invitationToken) { this.invitationToken = invitationToken; }

    public OffsetDateTime getInvitationExpiresAt() { return invitationExpiresAt; }
    public void setInvitationExpiresAt(OffsetDateTime invitationExpiresAt) { this.invitationExpiresAt = invitationExpiresAt; }

    public String getPasswordResetToken() { return passwordResetToken; }
    public void setPasswordResetToken(String passwordResetToken) { this.passwordResetToken = passwordResetToken; }

    public OffsetDateTime getPasswordResetExpiresAt() { return passwordResetExpiresAt; }
    public void setPasswordResetExpiresAt(OffsetDateTime passwordResetExpiresAt) { this.passwordResetExpiresAt = passwordResetExpiresAt; }

    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }

    public String getTwoFactorCode() { return twoFactorCode; }
    public void setTwoFactorCode(String twoFactorCode) { this.twoFactorCode = twoFactorCode; }

    public OffsetDateTime getTwoFactorCodeExpiresAt() { return twoFactorCodeExpiresAt; }
    public void setTwoFactorCodeExpiresAt(OffsetDateTime twoFactorCodeExpiresAt) { this.twoFactorCodeExpiresAt = twoFactorCodeExpiresAt; }

    public int getTwoFactorAttempts() { return twoFactorAttempts; }
    public void setTwoFactorAttempts(int twoFactorAttempts) { this.twoFactorAttempts = twoFactorAttempts; }

    public OffsetDateTime getTwoFactorLockedUntil() { return twoFactorLockedUntil; }
    public void setTwoFactorLockedUntil(OffsetDateTime twoFactorLockedUntil) { this.twoFactorLockedUntil = twoFactorLockedUntil; }

    public int getTwoFactorResendCount() { return twoFactorResendCount; }
    public void setTwoFactorResendCount(int twoFactorResendCount) { this.twoFactorResendCount = twoFactorResendCount; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public OffsetDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(OffsetDateTime lockedUntil) { this.lockedUntil = lockedUntil; }

    public OffsetDateTime getLastPasswordResetRequestAt() { return lastPasswordResetRequestAt; }
    public void setLastPasswordResetRequestAt(OffsetDateTime lastPasswordResetRequestAt) { this.lastPasswordResetRequestAt = lastPasswordResetRequestAt; }

    public int getPasswordResetCountHourly() { return passwordResetCountHourly; }
    public void setPasswordResetCountHourly(int passwordResetCountHourly) { this.passwordResetCountHourly = passwordResetCountHourly; }

    public OffsetDateTime getPasswordResetCountResetAt() { return passwordResetCountResetAt; }
    public void setPasswordResetCountResetAt(OffsetDateTime passwordResetCountResetAt) { this.passwordResetCountResetAt = passwordResetCountResetAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
