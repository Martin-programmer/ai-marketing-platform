package com.amp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Provides typed access to system_setting values with a 5-minute in-memory cache.
 */
@Service
public class SystemSettingService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingService.class);
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    private final SystemSettingRepository repo;

    private final ConcurrentHashMap<String, CachedValue> cache = new ConcurrentHashMap<>();
    private volatile long lastFullLoad = 0;
    private volatile Map<String, SystemSetting> fullCache = Map.of();

    public SystemSettingService(SystemSettingRepository repo) {
        this.repo = repo;
    }

    // ── Typed getters with fallback ──────────────────────────

    public String getString(String key, String defaultValue) {
        String val = getRawValue(key);
        return val != null ? val : defaultValue;
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public int getInt(String key, int defaultValue) {
        String val = getRawValue(key);
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public long getLong(String key, long defaultValue) {
        String val = getRawValue(key);
        if (val == null) return defaultValue;
        try { return Long.parseLong(val.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = getRawValue(key);
        if (val == null) return defaultValue;
        return "true".equalsIgnoreCase(val.trim());
    }

    public double getDecimal(String key, double defaultValue) {
        String val = getRawValue(key);
        if (val == null) return defaultValue;
        try { return Double.parseDouble(val.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
        String val = getRawValue(key);
        if (val == null) return defaultValue;
        try { return new BigDecimal(val.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    // ── Admin API methods ────────────────────────────────────

    public List<SystemSetting> getAll() {
        return repo.findAllByOrderByCategoryAscSettingKeyAsc();
    }

    public Map<String, List<SystemSetting>> getAllGroupedByCategory() {
        return getAll().stream()
                .collect(Collectors.groupingBy(SystemSetting::getCategory, LinkedHashMap::new, Collectors.toList()));
    }

    public List<SystemSetting> getByCategory(String category) {
        return repo.findByCategoryOrderBySettingKey(category);
    }

    public Optional<SystemSetting> getByKey(String key) {
        return repo.findBySettingKey(key);
    }

    public SystemSetting updateSetting(String key, String newValue, UUID updatedBy) {
        SystemSetting setting = repo.findBySettingKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + key));

        if (!setting.isEditable()) {
            throw new IllegalStateException("Setting '" + key + "' is not editable");
        }

        validateValueType(setting.getValueType(), newValue);

        setting.setSettingValue(newValue);
        setting.setUpdatedAt(OffsetDateTime.now());
        setting.setUpdatedBy(updatedBy);

        SystemSetting saved = repo.save(setting);

        // Invalidate cache for this key
        cache.remove(key);
        lastFullLoad = 0;

        log.info("System setting updated: {} = {} (by {})", key, newValue, updatedBy);
        return saved;
    }

    public void clearCache() {
        cache.clear();
        lastFullLoad = 0;
    }

    // ── Internal ─────────────────────────────────────────────

    private String getRawValue(String key) {
        CachedValue cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        try {
            Optional<SystemSetting> setting = repo.findBySettingKey(key);
            String val = setting.map(SystemSetting::getSettingValue).orElse(null);
            cache.put(key, new CachedValue(val));
            return val;
        } catch (Exception e) {
            log.warn("Failed to read system_setting '{}': {}", key, e.getMessage());
            return cached != null ? cached.value : null; // stale is better than nothing
        }
    }

    private void validateValueType(String valueType, String value) {
        try {
            switch (valueType) {
                case "INTEGER" -> Integer.parseInt(value.trim());
                case "DECIMAL" -> Double.parseDouble(value.trim());
                case "BOOLEAN" -> {
                    if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim())) {
                        throw new IllegalArgumentException("Boolean value must be 'true' or 'false'");
                    }
                }
                case "JSON" -> {
                    // Basic JSON validation: must start with { or [
                    String trimmed = value.trim();
                    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                        throw new IllegalArgumentException("JSON value must start with { or [");
                    }
                }
                // STRING: accept anything
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Value '" + value + "' is not valid for type " + valueType);
        }
    }

    private record CachedValue(String value, long timestamp) {
        CachedValue(String value) { this(value, System.currentTimeMillis()); }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_TTL_MS; }
    }
}
