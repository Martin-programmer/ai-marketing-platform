package com.amp.clients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code user_client_permission} table (V001).
 * Composite PK: (userId, clientId, permission).
 */
@Entity
@Table(name = "user_client_permission")
@IdClass(UserClientPermission.PK.class)
public class UserClientPermission {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Id
    @Column(name = "permission", nullable = false)
    private String permission;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected UserClientPermission() {}

    public UserClientPermission(UUID userId, UUID clientId, String permission) {
        this.userId = userId;
        this.clientId = clientId;
        this.permission = permission;
        this.createdAt = OffsetDateTime.now();
    }

    // ---- getters ----

    public UUID getUserId() { return userId; }
    public UUID getClientId() { return clientId; }
    public String getPermission() { return permission; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    // ---- composite PK class ----

    public static class PK implements Serializable {
        private UUID userId;
        private UUID clientId;
        private String permission;

        public PK() {}

        public PK(UUID userId, UUID clientId, String permission) {
            this.userId = userId;
            this.clientId = clientId;
            this.permission = permission;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(userId, pk.userId)
                    && Objects.equals(clientId, pk.clientId)
                    && Objects.equals(permission, pk.permission);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, clientId, permission);
        }
    }
}
