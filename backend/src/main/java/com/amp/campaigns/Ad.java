package com.amp.campaigns;

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
 * JPA entity mapped to the {@code ad} table.
 */
@Entity
@Table(name = "ad")
public class Ad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "adset_id", nullable = false)
    private UUID adsetId;

    @Column(name = "meta_ad_id")
    private String metaAdId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "creative_package_item_id")
    private UUID creativePackageItemId;

    @Column(name = "creative_asset_id")
    private UUID creativeAssetId;

    @Column(name = "copy_variant_id")
    private UUID copyVariantId;

    @Column(name = "primary_text")
    private String primaryText;

    @Column(name = "headline")
    private String headline;

    @Column(name = "description")
    private String description;

    @Column(name = "cta")
    private String cta;

    @Column(name = "destination_url")
    private String destinationUrl;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Ad() {}

    @PreUpdate
    private void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    // ---- getters & setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public UUID getAdsetId() { return adsetId; }
    public void setAdsetId(UUID adsetId) { this.adsetId = adsetId; }

    public String getMetaAdId() { return metaAdId; }
    public void setMetaAdId(String metaAdId) { this.metaAdId = metaAdId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getCreativePackageItemId() { return creativePackageItemId; }
    public void setCreativePackageItemId(UUID creativePackageItemId) { this.creativePackageItemId = creativePackageItemId; }

    public UUID getCreativeAssetId() { return creativeAssetId; }
    public void setCreativeAssetId(UUID creativeAssetId) { this.creativeAssetId = creativeAssetId; }

    public UUID getCopyVariantId() { return copyVariantId; }
    public void setCopyVariantId(UUID copyVariantId) { this.copyVariantId = copyVariantId; }

    public String getPrimaryText() { return primaryText; }
    public void setPrimaryText(String primaryText) { this.primaryText = primaryText; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCta() { return cta; }
    public void setCta(String cta) { this.cta = cta; }

    public String getDestinationUrl() { return destinationUrl; }
    public void setDestinationUrl(String destinationUrl) { this.destinationUrl = destinationUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
