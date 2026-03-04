package com.amp.ai;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service handling AI suggestion lifecycle operations.
 */
@Service
@Transactional
public class AiSuggestionService {

    private final AiSuggestionRepository suggestionRepository;
    private final AiActionLogRepository actionLogRepository;
    private final AuditService auditService;

    public AiSuggestionService(AiSuggestionRepository suggestionRepository,
                               AiActionLogRepository actionLogRepository,
                               AuditService auditService) {
        this.suggestionRepository = suggestionRepository;
        this.actionLogRepository = actionLogRepository;
        this.auditService = auditService;
    }

    // ──────── Query ────────

    @Transactional(readOnly = true)
    public List<SuggestionResponse> listSuggestions(UUID agencyId, UUID clientId, String status) {
        List<AiSuggestion> list;
        if (status == null || status.isBlank()) {
            list = suggestionRepository.findAllByAgencyIdAndClientId(agencyId, clientId);
        } else {
            list = suggestionRepository.findAllByAgencyIdAndClientIdAndStatus(agencyId, clientId, status);
        }
        return list.stream().map(SuggestionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SuggestionResponse getSuggestion(UUID agencyId, UUID suggestionId) {
        AiSuggestion s = findOrThrow(agencyId, suggestionId);
        return SuggestionResponse.from(s);
    }

    // ──────── Mutations ────────

    public SuggestionResponse updateSuggestion(UUID agencyId, UUID suggestionId,
                                               UpdateSuggestionRequest request) {
        AiSuggestion s = findOrThrow(agencyId, suggestionId);
        requirePending(s);

        if (request.payloadJson() != null) {
            s.setPayloadJson(request.payloadJson());
        }
        if (request.confidence() != null) {
            s.setConfidence(request.confidence());
        }

        AiSuggestion saved = suggestionRepository.save(s);
        return SuggestionResponse.from(saved);
    }

    public SuggestionResponse approveSuggestion(UUID agencyId, UUID suggestionId) {
        TenantContext ctx = TenantContextHolder.require();
        AiSuggestion s = findOrThrow(agencyId, suggestionId);
        requirePending(s);

        s.setStatus("APPROVED");
        s.setReviewedBy(ctx.getUserId());
        s.setReviewedAt(OffsetDateTime.now());
        AiSuggestion saved = suggestionRepository.save(s);

        auditService.log(agencyId, s.getClientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.SUGGESTION_APPROVE, "AiSuggestion", suggestionId,
                "PENDING", "APPROVED", null);

        return SuggestionResponse.from(saved);
    }

    public SuggestionResponse rejectSuggestion(UUID agencyId, UUID suggestionId) {
        TenantContext ctx = TenantContextHolder.require();
        AiSuggestion s = findOrThrow(agencyId, suggestionId);
        requirePending(s);

        s.setStatus("REJECTED");
        s.setReviewedBy(ctx.getUserId());
        s.setReviewedAt(OffsetDateTime.now());
        AiSuggestion saved = suggestionRepository.save(s);

        auditService.log(agencyId, s.getClientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.SUGGESTION_REJECT, "AiSuggestion", suggestionId,
                "PENDING", "REJECTED", null);

        return SuggestionResponse.from(saved);
    }

    public SuggestionResponse applySuggestion(UUID agencyId, UUID suggestionId) {
        TenantContext ctx = TenantContextHolder.require();
        AiSuggestion s = findOrThrow(agencyId, suggestionId);

        if (!"APPROVED".equals(s.getStatus())) {
            throw new IllegalStateException("Only APPROVED suggestions can be applied");
        }

        s.setStatus("APPLIED");
        AiSuggestion saved = suggestionRepository.save(s);

        // Create action log record
        AiActionLog log = new AiActionLog();
        log.setAgencyId(agencyId);
        log.setClientId(s.getClientId());
        log.setSuggestionId(suggestionId);
        log.setExecutedBy("USER");
        log.setMetaRequestJson("{}");
        log.setSuccess(true);
        log.setCreatedAt(OffsetDateTime.now());
        actionLogRepository.save(log);

        auditService.log(agencyId, s.getClientId(), ctx.getUserId(), ctx.getRole(),
                AuditAction.SUGGESTION_APPLY, "AiSuggestion", suggestionId,
                "APPROVED", "APPLIED", null);

        return SuggestionResponse.from(saved);
    }

    // ──────── Action Logs ────────

    @Transactional(readOnly = true)
    public List<ActionLogResponse> getActionLogs(UUID suggestionId) {
        return actionLogRepository.findAllBySuggestionId(suggestionId)
                .stream().map(ActionLogResponse::from).toList();
    }

    // ──────── Helpers ────────

    /**
     * Resolves the clientId that owns a given suggestion (used for permission checks).
     */
    @Transactional(readOnly = true)
    public UUID resolveClientId(UUID agencyId, UUID suggestionId) {
        return findOrThrow(agencyId, suggestionId).getClientId();
    }

    private AiSuggestion findOrThrow(UUID agencyId, UUID suggestionId) {
        return suggestionRepository.findByIdAndAgencyId(suggestionId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("AiSuggestion", suggestionId));
    }

    private void requirePending(AiSuggestion s) {
        if (!"PENDING".equals(s.getStatus())) {
            throw new IllegalStateException("Suggestion must be in PENDING status, current: " + s.getStatus());
        }
    }
}
