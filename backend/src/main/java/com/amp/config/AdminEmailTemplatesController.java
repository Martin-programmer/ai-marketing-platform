package com.amp.config;

import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin API for managing email templates. OWNER_ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/owner/email-templates")
public class AdminEmailTemplatesController {

    private final EmailTemplateDbService templateService;

    public AdminEmailTemplatesController(EmailTemplateDbService templateService) {
        this.templateService = templateService;
    }

    /** List all active email templates. */
    @GetMapping
    public ResponseEntity<?> getAll() {
        RoleGuard.requireOwnerAdmin();
        return ResponseEntity.ok(templateService.getAll());
    }

    /** Get a single template by key. */
    @GetMapping("/{templateKey}")
    public ResponseEntity<?> getByKey(@PathVariable String templateKey) {
        RoleGuard.requireOwnerAdmin();
        return templateService.getTemplate(templateKey.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Update a template's subject and HTML body. */
    @PutMapping("/{templateKey}")
    public ResponseEntity<?> update(@PathVariable String templateKey,
                                    @RequestBody UpdateEmailTemplateRequest req) {
        RoleGuard.requireOwnerAdmin();
        UUID userId = TenantContextHolder.require().getUserId();
        try {
            EmailTemplateEntity updated = templateService.updateTemplate(
                    templateKey.toUpperCase(), req.subject(), req.htmlBody(), userId);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Preview a template rendered with sample data, wrapped in the outer template. */
    @PostMapping("/{templateKey}/preview")
    public ResponseEntity<?> preview(@PathVariable String templateKey) {
        RoleGuard.requireOwnerAdmin();
        try {
            Map<String, String> rendered = templateService.previewTemplate(templateKey.toUpperCase());
            return ResponseEntity.ok(rendered);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Clear the email template cache. */
    @PostMapping("/cache/clear")
    public ResponseEntity<?> clearCache() {
        RoleGuard.requireOwnerAdmin();
        templateService.clearCache();
        return ResponseEntity.ok(Map.of("message", "Email template cache cleared"));
    }

    public record UpdateEmailTemplateRequest(String subject, String htmlBody) {}
}
