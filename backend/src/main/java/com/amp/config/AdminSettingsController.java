package com.amp.config;

import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin API for managing system settings. OWNER_ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/owner/settings")
public class AdminSettingsController {

    private final SystemSettingService settingService;

    public AdminSettingsController(SystemSettingService settingService) {
        this.settingService = settingService;
    }

    /** List all settings grouped by category. */
    @GetMapping
    public ResponseEntity<?> getAll() {
        RoleGuard.requireOwnerAdmin();
        return ResponseEntity.ok(settingService.getAllGroupedByCategory());
    }

    /** List settings for a specific category. */
    @GetMapping("/category/{category}")
    public ResponseEntity<?> getByCategory(@PathVariable String category) {
        RoleGuard.requireOwnerAdmin();
        return ResponseEntity.ok(settingService.getByCategory(category.toUpperCase()));
    }

    /** Get a single setting by key. */
    @GetMapping("/{key}")
    public ResponseEntity<?> getByKey(@PathVariable String key) {
        RoleGuard.requireOwnerAdmin();
        return settingService.getByKey(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Update a single setting. */
    @PutMapping("/{key}")
    public ResponseEntity<?> update(@PathVariable String key,
                                    @RequestBody UpdateSettingRequest req) {
        RoleGuard.requireOwnerAdmin();
        UUID userId = TenantContextHolder.require().getUserId();
        try {
            SystemSetting updated = settingService.updateSetting(key, req.value(), userId);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /** Clear the in-memory cache. */
    @PostMapping("/cache/clear")
    public ResponseEntity<?> clearCache() {
        RoleGuard.requireOwnerAdmin();
        settingService.clearCache();
        return ResponseEntity.ok(Map.of("message", "Settings cache cleared"));
    }

    public record UpdateSettingRequest(String value) {}
}
