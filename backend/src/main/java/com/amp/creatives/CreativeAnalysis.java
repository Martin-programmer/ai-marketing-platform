package com.amp.creatives;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code creative_analysis} table.
 * Stores the AI analysis result for a creative asset.
 */
@Entity
@Table(name = "creative_analysis")
public class CreativeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "creative_asset_id", nullable = false)
    private UUID creativeAssetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_json", nullable = false, columnDefinition = "jsonb")
    private String analysisJson;

    @Column(name = "quality_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public CreativeAnalysis() {}

    // ---- getters & setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public UUID getCreativeAssetId() { return creativeAssetId; }
    public void setCreativeAssetId(UUID creativeAssetId) { this.creativeAssetId = creativeAssetId; }

    public String getAnalysisJson() { return analysisJson; }
    public void setAnalysisJson(String analysisJson) { this.analysisJson = analysisJson; }

    public BigDecimal getQualityScore() { return qualityScore; }
    public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
