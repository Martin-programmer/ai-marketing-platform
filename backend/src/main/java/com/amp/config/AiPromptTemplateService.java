package com.amp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages AI prompt templates with versioning, caching (5-min TTL),
 * and variable substitution.
 */
@Service
public class AiPromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(AiPromptTemplateService.class);
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private final AiPromptTemplateRepository repo;
    private final ConcurrentHashMap<String, CachedPrompt> cache = new ConcurrentHashMap<>();

    public AiPromptTemplateService(AiPromptTemplateRepository repo) {
        this.repo = repo;
    }

    // ── Read (cached) ────────────────────────────────────────

    /** Get the active prompt text, or null if not found. */
    public String getActivePromptText(String module, String promptName) {
        String cacheKey = module + "::" + promptName;
        CachedPrompt cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.text;
        }

        try {
            Optional<AiPromptTemplate> tpl = repo.findByModuleAndPromptNameAndIsActiveTrue(module, promptName);
            String text = tpl.map(AiPromptTemplate::getPromptText).orElse(null);
            cache.put(cacheKey, new CachedPrompt(text));
            return text;
        } catch (Exception e) {
            log.warn("Failed to load prompt {}/{}: {}", module, promptName, e.getMessage());
            return cached != null ? cached.text : null;
        }
    }

    /** Get the active prompt text with a hardcoded fallback. */
    public String getActivePromptText(String module, String promptName, String fallback) {
        String text = getActivePromptText(module, promptName);
        return text != null ? text : fallback;
    }

    /** Render a prompt template, replacing {key} placeholders with supplied values. */
    public String renderPrompt(String module, String promptName,
                               Map<String, String> variables, String fallbackTemplate) {
        String template = getActivePromptText(module, promptName, fallbackTemplate);
        if (template == null) return null;
        for (Map.Entry<String, String> e : variables.entrySet()) {
            template = template.replace("{" + e.getKey() + "}", e.getValue());
        }
        return template;
    }

    // ── Admin API ────────────────────────────────────────────

    public List<AiPromptTemplate> getAllActive() {
        return repo.findByIsActiveTrueOrderByModuleAscPromptNameAsc();
    }

    public Map<String, List<AiPromptTemplate>> getAllActiveGroupedByModule() {
        return getAllActive().stream()
                .collect(Collectors.groupingBy(AiPromptTemplate::getModule,
                        LinkedHashMap::new, Collectors.toList()));
    }

    public List<AiPromptTemplate> getActiveByModule(String module) {
        return repo.findByModuleAndIsActiveTrueOrderByPromptName(module);
    }

    /**
     * Creates a new version of a prompt. The old version is deactivated,
     * and the new one becomes the active version.
     */
    public AiPromptTemplate updatePrompt(String module, String promptName,
                                         String newText, UUID updatedBy) {
        Optional<AiPromptTemplate> current =
                repo.findByModuleAndPromptNameAndIsActiveTrue(module, promptName);

        int nextVersion = 1;
        String description = "";

        if (current.isPresent()) {
            AiPromptTemplate old = current.get();
            nextVersion = old.getVersion() + 1;
            description = old.getDescription();
            old.setActive(false);
            old.setUpdatedAt(OffsetDateTime.now());
            old.setUpdatedBy(updatedBy);
            repo.save(old);
        }

        AiPromptTemplate next = new AiPromptTemplate();
        next.setModule(module);
        next.setPromptName(promptName);
        next.setPromptText(newText);
        next.setDescription(description);
        next.setVersion(nextVersion);
        next.setActive(true);
        next.setCreatedAt(OffsetDateTime.now());
        next.setUpdatedAt(OffsetDateTime.now());
        next.setUpdatedBy(updatedBy);

        AiPromptTemplate saved = repo.save(next);
        cache.remove(module + "::" + promptName);
        log.info("Prompt updated: {}/{} → v{} (by {})", module, promptName, nextVersion, updatedBy);
        return saved;
    }

    /** Full version history (newest first). */
    public List<AiPromptTemplate> getPromptHistory(String module, String promptName) {
        return repo.findByModuleAndPromptNameOrderByVersionDesc(module, promptName);
    }

    /** Revert to a specific version: deactivate current, reactivate the target. */
    public AiPromptTemplate revertToVersion(String module, String promptName,
                                            int version, UUID updatedBy) {
        Optional<AiPromptTemplate> target =
                repo.findByModuleAndPromptNameAndVersion(module, promptName, version);
        if (target.isEmpty()) {
            throw new IllegalArgumentException(
                    "Version " + version + " not found for " + module + "/" + promptName);
        }

        // Deactivate current active
        repo.findByModuleAndPromptNameAndIsActiveTrue(module, promptName)
                .ifPresent(current -> {
                    current.setActive(false);
                    current.setUpdatedAt(OffsetDateTime.now());
                    current.setUpdatedBy(updatedBy);
                    repo.save(current);
                });

        // Reactivate target
        AiPromptTemplate t = target.get();
        t.setActive(true);
        t.setUpdatedAt(OffsetDateTime.now());
        t.setUpdatedBy(updatedBy);
        AiPromptTemplate saved = repo.save(t);

        cache.remove(module + "::" + promptName);
        log.info("Prompt reverted: {}/{} → v{} (by {})", module, promptName, version, updatedBy);
        return saved;
    }

    public void clearCache() {
        cache.clear();
    }

    private record CachedPrompt(String text, long timestamp) {
        CachedPrompt(String text) { this(text, System.currentTimeMillis()); }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_TTL_MS; }
    }
}
