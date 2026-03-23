package com.amp.config;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_template")
public class EmailTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "template_key", nullable = false, unique = true)
    private String templateKey;

    @Column(nullable = false)
    private String subject;

    @Column(name = "html_body", nullable = false, columnDefinition = "text")
    private String htmlBody;

    private String description;

    @Column(name = "available_vars")
    private String availableVars;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by")
    private UUID updatedBy;

    // ── Getters & Setters ──

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getHtmlBody() { return htmlBody; }
    public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAvailableVars() { return availableVars; }
    public void setAvailableVars(String availableVars) { this.availableVars = availableVars; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
