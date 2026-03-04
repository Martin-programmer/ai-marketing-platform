package com.amp.clients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code user_client_permission} table (V008).
 * <p>
 * UUID primary key with a unique constraint on (user_id, client_id, permission).
 * Stores granular {@link com.amp.auth.Permission} values per user per client.
 */
@Entity
@Table(name = "user_client_permission")
public class UserClientPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String permission;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public UserClientPermission() {}

    public UserClientPermission(UUID userId, UUID clientId, String permission) {
        this.userId = userId;
        this.clientId = clientId;
        this.permission = permission;
        this.createdAt = OffsetDateTime.now();
    }

    public UserClientPermission(UUID userId, UUID clientId, String permission, UUID grantedBy) {
        this(userId, clientId, permission);
        this.grantedBy = grantedBy;
    }

    // ---- getters & setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    public UUID getGrantedBy() { return grantedBy; }
    public void setGrantedBy(UUID grantedBy) { this.grantedBy = grantedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
