package com.amp.audit;

import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for querying the audit trail.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * List audit logs for the current agency, with optional filters.
     *
     * @param entityType filter by entity type (e.g. "Campaign", "Client")
     * @param action     filter by action (e.g. "CLIENT_CREATE", "CAMPAIGN_PUBLISH")
     * @param entityId   filter by specific entity ID
     * @param clientId   filter by client ID
     * @param limit      max results (default 100, max 500)
     */
    @GetMapping
    public List<AuditLogResponse> listAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return auditService.queryLogs(agencyId, entityType, action, entityId, clientId, safeLimit);
    }
}
