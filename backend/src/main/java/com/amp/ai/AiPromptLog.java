package com.amp.ai;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_prompt_log")
public class AiPromptLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id") private UUID agencyId;
    @Column(name = "client_id") private UUID clientId;
    @Column(nullable = false) private String module;
    @Column(nullable = false) private String model;
    @Column(name = "prompt_tokens") private int promptTokens;
    @Column(name = "completion_tokens") private int completionTokens;
    @Column(name = "total_tokens") private int totalTokens;
    @Column(name = "cost_usd", precision = 10, scale = 6) private BigDecimal costUsd = BigDecimal.ZERO;
    @Column(name = "duration_ms") private int durationMs;
    @Column(nullable = false) private boolean success;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAgencyId() { return agencyId; }
    public void setAgencyId(UUID agencyId) { this.agencyId = agencyId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int v) { this.promptTokens = v; }
    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int v) { this.completionTokens = v; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int v) { this.totalTokens = v; }
    public BigDecimal getCostUsd() { return costUsd; }
    public void setCostUsd(BigDecimal v) { this.costUsd = v; }
    public int getDurationMs() { return durationMs; }
    public void setDurationMs(int v) { this.durationMs = v; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean v) { this.success = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
