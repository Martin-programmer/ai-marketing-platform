package com.amp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads email templates from the database, caches them (5-min TTL),
 * and renders them by replacing {{placeholder}} variables.
 */
@Service
public class EmailTemplateDbService {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateDbService.class);
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private final EmailTemplateRepository repo;
    private final ConcurrentHashMap<String, CachedTemplate> cache = new ConcurrentHashMap<>();

    public EmailTemplateDbService(EmailTemplateRepository repo) {
        this.repo = repo;
    }

    // ── Read ─────────────────────────────────────────────────

    /** Get the active template entity (cached). */
    public Optional<EmailTemplateEntity> getTemplate(String templateKey) {
        CachedTemplate cached = cache.get(templateKey);
        if (cached != null && !cached.isExpired()) {
            return Optional.ofNullable(cached.entity);
        }

        try {
            Optional<EmailTemplateEntity> tpl = repo.findByTemplateKeyAndIsActiveTrue(templateKey);
            cache.put(templateKey, new CachedTemplate(tpl.orElse(null)));
            return tpl;
        } catch (Exception e) {
            log.warn("Failed to load email template '{}': {}", templateKey, e.getMessage());
            return cached != null ? Optional.ofNullable(cached.entity) : Optional.empty();
        }
    }

    /** Get the template's HTML body, or fallback if not in DB. */
    public String getTemplateBody(String templateKey, String fallback) {
        return getTemplate(templateKey)
                .map(EmailTemplateEntity::getHtmlBody)
                .orElse(fallback);
    }

    /** Get the template's subject, or fallback. */
    public String getTemplateSubject(String templateKey, String fallbackSubject) {
        return getTemplate(templateKey)
                .map(EmailTemplateEntity::getSubject)
                .orElse(fallbackSubject);
    }

    /**
     * Render a template by replacing all {{key}} placeholders with the supplied values.
     * Returns the rendered HTML body.
     */
    public String renderBody(String templateKey, Map<String, String> variables, String fallbackBody) {
        String body = getTemplateBody(templateKey, fallbackBody);
        if (body == null) return null;
        return replaceVariables(body, variables);
    }

    /**
     * Render a subject line by replacing {{key}} placeholders.
     */
    public String renderSubject(String templateKey, Map<String, String> variables, String fallbackSubject) {
        String subject = getTemplateSubject(templateKey, fallbackSubject);
        if (subject == null) return null;
        return replaceVariables(subject, variables);
    }

    /** Get the outer wrapper template. The wrapper uses {{content}} as placeholder. */
    public String getOuterWrapper(String fallback) {
        return getTemplateBody("OUTER_WRAPPER", fallback);
    }

    // ── Admin API ────────────────────────────────────────────

    public List<EmailTemplateEntity> getAll() {
        return repo.findByIsActiveTrueOrderByTemplateKey();
    }

    public EmailTemplateEntity updateTemplate(String templateKey, String subject,
                                              String htmlBody, UUID updatedBy) {
        EmailTemplateEntity tpl = repo.findByTemplateKeyAndIsActiveTrue(templateKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown template: " + templateKey));

        tpl.setSubject(subject);
        tpl.setHtmlBody(htmlBody);
        tpl.setUpdatedAt(OffsetDateTime.now());
        tpl.setUpdatedBy(updatedBy);

        EmailTemplateEntity saved = repo.save(tpl);
        cache.remove(templateKey);
        log.info("Email template updated: {} (by {})", templateKey, updatedBy);
        return saved;
    }

    /** Render a template with sample data for preview purposes. */
    public Map<String, String> previewTemplate(String templateKey) {
        EmailTemplateEntity tpl = repo.findByTemplateKeyAndIsActiveTrue(templateKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown template: " + templateKey));

        Map<String, String> sampleVars = buildSampleVars(tpl.getAvailableVars());
        String renderedSubject = replaceVariables(tpl.getSubject(), sampleVars);
        String renderedBody = replaceVariables(tpl.getHtmlBody(), sampleVars);

        // Wrap in outer wrapper if not the wrapper itself
        if (!"OUTER_WRAPPER".equals(templateKey)) {
            String wrapper = getOuterWrapper("<html><body>{{content}}</body></html>");
            renderedBody = wrapper.replace("{{content}}", renderedBody);
        }

        return Map.of(
                "subject", renderedSubject,
                "body", renderedBody
        );
    }

    public void clearCache() {
        cache.clear();
    }

    // ── Internal ─────────────────────────────────────────────

    private String replaceVariables(String template, Map<String, String> variables) {
        for (Map.Entry<String, String> e : variables.entrySet()) {
            template = template.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return template;
    }

    private Map<String, String> buildSampleVars(String availableVars) {
        Map<String, String> sample = new LinkedHashMap<>();
        if (availableVars == null || availableVars.isBlank()) return sample;
        for (String v : availableVars.split(",")) {
            String key = v.trim();
            sample.put(key, "[" + key + "]");
        }
        return sample;
    }

    private record CachedTemplate(EmailTemplateEntity entity, long timestamp) {
        CachedTemplate(EmailTemplateEntity entity) { this(entity, System.currentTimeMillis()); }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_TTL_MS; }
    }
}
