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

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
