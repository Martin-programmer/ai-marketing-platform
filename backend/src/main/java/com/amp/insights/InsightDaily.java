package com.amp.insights;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code insight_daily} table (V004).
 */
@Entity
@Table(name = "insight_daily")
public class InsightDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "spend", nullable = false, precision = 18, scale = 2)
    private BigDecimal spend;

    @Column(name = "impressions", nullable = false)
    private long impressions;

    @Column(name = "clicks", nullable = false)
    private long clicks;

    @Column(name = "ctr", nullable = false, precision = 10, scale = 6)
    private BigDecimal ctr;

    @Column(name = "cpc", nullable = false, precision = 18, scale = 6)
    private BigDecimal cpc;

    @Column(name = "cpm", nullable = false, precision = 18, scale = 6)
    private BigDecimal cpm;

    @Column(name = "conversions", nullable = false, precision = 18, scale = 6)
    private BigDecimal conversions;

    @Column(name = "conversion_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal conversionValue;

    @Column(name = "roas", nullable = false, precision = 18, scale = 6)
    private BigDecimal roas;

    @Column(name = "frequency", nullable = false, precision = 18, scale = 6)
    private BigDecimal frequency;

    @Column(name = "reach", nullable = false)
    private long reach;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", columnDefinition = "jsonb")
    private String rawJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected InsightDaily() {}

    // ---- getters & setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public BigDecimal getSpend() { return spend; }
    public void setSpend(BigDecimal spend) { this.spend = spend; }

    public long getImpressions() { return impressions; }
    public void setImpressions(long impressions) { this.impressions = impressions; }

    public long getClicks() { return clicks; }
    public void setClicks(long clicks) { this.clicks = clicks; }

    public BigDecimal getCtr() { return ctr; }
    public void setCtr(BigDecimal ctr) { this.ctr = ctr; }

    public BigDecimal getCpc() { return cpc; }
    public void setCpc(BigDecimal cpc) { this.cpc = cpc; }

    public BigDecimal getCpm() { return cpm; }
    public void setCpm(BigDecimal cpm) { this.cpm = cpm; }

    public BigDecimal getConversions() { return conversions; }
    public void setConversions(BigDecimal conversions) { this.conversions = conversions; }

    public BigDecimal getConversionValue() { return conversionValue; }
    public void setConversionValue(BigDecimal conversionValue) { this.conversionValue = conversionValue; }

    public BigDecimal getRoas() { return roas; }
    public void setRoas(BigDecimal roas) { this.roas = roas; }

    public BigDecimal getFrequency() { return frequency; }
    public void setFrequency(BigDecimal frequency) { this.frequency = frequency; }

    public long getReach() { return reach; }
    public void setReach(long reach) { this.reach = reach; }

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
