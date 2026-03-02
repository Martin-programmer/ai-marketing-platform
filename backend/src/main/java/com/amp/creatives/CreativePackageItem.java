package com.amp.creatives;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code creative_package_item} table.
 * Links a specific asset + copy variant within a package.
 */
@Entity
@Table(name = "creative_package_item")
public class CreativePackageItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "creative_asset_id", nullable = false)
    private UUID creativeAssetId;

    @Column(name = "copy_variant_id", nullable = false)
    private UUID copyVariantId;

    @Column(name = "weight", nullable = false)
    private Integer weight;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected CreativePackageItem() {}

    // ---- getters & setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public UUID getPackageId() { return packageId; }
    public void setPackageId(UUID packageId) { this.packageId = packageId; }

    public UUID getCreativeAssetId() { return creativeAssetId; }
    public void setCreativeAssetId(UUID creativeAssetId) { this.creativeAssetId = creativeAssetId; }

    public UUID getCopyVariantId() { return copyVariantId; }
    public void setCopyVariantId(UUID copyVariantId) { this.copyVariantId = copyVariantId; }

    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
