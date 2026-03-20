package com.amp.campaigns;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code campaign} table.
 */
@Entity
@Table(name = "campaign")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "meta_campaign_id")
    private String metaCampaignId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "objective", nullable = false)
    private String objective;

    @Column(name = "budget_type", nullable = false)
    private String budgetType;

    @Column(name = "daily_budget", precision = 18, scale = 6)
    private BigDecimal dailyBudget;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Campaign() {}

    @PreUpdate
    private void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    // ---- getters & setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getMetaCampaignId() { return metaCampaignId; }
    public void setMetaCampaignId(String metaCampaignId) { this.metaCampaignId = metaCampaignId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }

    public String getBudgetType() { return budgetType; }
    public void setBudgetType(String budgetType) { this.budgetType = budgetType; }

    public BigDecimal getDailyBudget() { return dailyBudget; }
    public void setDailyBudget(BigDecimal dailyBudget) { this.dailyBudget = dailyBudget; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
