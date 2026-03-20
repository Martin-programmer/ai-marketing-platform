package com.amp.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code ai_action_log} table (V005).
 */
@Entity
@Table(name = "ai_action_log")
public class AiActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "suggestion_id")
    private UUID suggestionId;

    @Column(name = "executed_by", nullable = false)
    private String executedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta_request_json", nullable = false, columnDefinition = "jsonb")
    private String metaRequestJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta_response_json", columnDefinition = "jsonb")
    private String metaResponseJson;

    @Column(name = "success", nullable = false)
    private boolean success;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot_json", columnDefinition = "jsonb")
    private String resultSnapshotJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AiActionLog() {}

    // ---- getters & setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public UUID getSuggestionId() { return suggestionId; }
    public void setSuggestionId(UUID suggestionId) { this.suggestionId = suggestionId; }

    public String getExecutedBy() { return executedBy; }
    public void setExecutedBy(String executedBy) { this.executedBy = executedBy; }

    public String getMetaRequestJson() { return metaRequestJson; }
    public void setMetaRequestJson(String metaRequestJson) { this.metaRequestJson = metaRequestJson; }

    public String getMetaResponseJson() { return metaResponseJson; }
    public void setMetaResponseJson(String metaResponseJson) { this.metaResponseJson = metaResponseJson; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getResultSnapshotJson() { return resultSnapshotJson; }
    public void setResultSnapshotJson(String resultSnapshotJson) { this.resultSnapshotJson = resultSnapshotJson; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
