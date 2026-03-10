package com.amp.creatives;

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
 * JPA entity mapped to the {@code copy_variant} table.
 * Represents a text variant for an ad (primary text, headline, description).
 */
@Entity
@Table(name = "copy_variant")
public class CopyVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "creative_asset_id")
    private UUID creativeAssetId;

    @Column(name = "language", nullable = false)
    private String language;

    @Column(name = "primary_text", nullable = false)
    private String primaryText;

    @Column(name = "headline", nullable = false)
    private String headline;

    @Column(name = "description")
    private String description;

    @Column(name = "cta", nullable = false)
    private String cta;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public CopyVariant() {}

    @PreUpdate
    private void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    // ---- getters & setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public UUID getCreativeAssetId() { return creativeAssetId; }
    public void setCreativeAssetId(UUID creativeAssetId) { this.creativeAssetId = creativeAssetId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getPrimaryText() { return primaryText; }
    public void setPrimaryText(String primaryText) { this.primaryText = primaryText; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCta() { return cta; }
    public void setCta(String cta) { this.cta = cta; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
