package com.amp.config;

import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin API for managing AI prompt templates with versioning. OWNER_ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/owner/prompts")
public class AdminPromptsController {

    private final AiPromptTemplateService promptService;

    public AdminPromptsController(AiPromptTemplateService promptService) {
        this.promptService = promptService;
    }

    /** List all active prompts grouped by module. */
    @GetMapping
    public ResponseEntity<?> getAll() {
        RoleGuard.requireOwnerAdmin();
        return ResponseEntity.ok(promptService.getAllActiveGroupedByModule());
    }

    /** List active prompts for a specific module. */
    @GetMapping("/{module}")
    public ResponseEntity<?> getByModule(@PathVariable String module) {
        RoleGuard.requireOwnerAdmin();
        return ResponseEntity.ok(promptService.getActiveByModule(module.toUpperCase()));
    }

    /** Get a single active prompt. */
    @GetMapping("/{module}/{promptName}")
    public ResponseEntity<?> getPrompt(@PathVariable String module,
                                       @PathVariable String promptName) {
        RoleGuard.requireOwnerAdmin();
        String text = promptService.getActivePromptText(module.toUpperCase(), promptName);
        if (text == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("module", module.toUpperCase(),
                "promptName", promptName, "promptText", text));
    }

    /** Update a prompt (creates a new version, deactivates the previous). */
    @PutMapping("/{module}/{promptName}")
    public ResponseEntity<?> updatePrompt(@PathVariable String module,
                                          @PathVariable String promptName,
                                          @RequestBody UpdatePromptRequest req) {
        RoleGuard.requireOwnerAdmin();
        UUID userId = TenantContextHolder.require().getUserId();
        try {
            AiPromptTemplate updated = promptService.updatePrompt(
                    module.toUpperCase(), promptName, req.promptText(), userId);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Get version history for a prompt (newest first). */
    @GetMapping("/{module}/{promptName}/history")
    public ResponseEntity<?> getHistory(@PathVariable String module,
                                        @PathVariable String promptName) {
        RoleGuard.requireOwnerAdmin();
        return ResponseEntity.ok(promptService.getPromptHistory(
                module.toUpperCase(), promptName));
    }

    /** Revert to a specific version. */
    @PostMapping("/{module}/{promptName}/revert/{version}")
    public ResponseEntity<?> revert(@PathVariable String module,
                                    @PathVariable String promptName,
                                    @PathVariable int version) {
        RoleGuard.requireOwnerAdmin();
        UUID userId = TenantContextHolder.require().getUserId();
        try {
            AiPromptTemplate reverted = promptService.revertToVersion(
                    module.toUpperCase(), promptName, version, userId);
            return ResponseEntity.ok(reverted);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Clear the prompt template cache. */
    @PostMapping("/cache/clear")
    public ResponseEntity<?> clearCache() {
        RoleGuard.requireOwnerAdmin();
        promptService.clearCache();
        return ResponseEntity.ok(Map.of("message", "Prompt cache cleared"));
    }

    public record UpdatePromptRequest(String promptText) {}
}
