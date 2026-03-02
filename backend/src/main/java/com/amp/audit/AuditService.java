package com.amp.audit;

import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service responsible for persisting audit log entries.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records an audit event.
     *
     * @param agencyId      tenant agency (nullable for owner-admin global actions)
     * @param clientId      related client (nullable)
     * @param actorUserId   acting user (nullable for system jobs)
     * @param actorRole     role of the actor
     * @param action        the auditable action
     * @param entityType    type of entity being acted upon
     * @param entityId      id of the entity (nullable)
     * @param before        state before the action (serialised to JSON, nullable)
     * @param after         state after the action (serialised to JSON, nullable)
     * @param correlationId request/trace correlation id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID agencyId,
                    UUID clientId,
                    UUID actorUserId,
                    String actorRole,
                    AuditAction action,
                    String entityType,
                    UUID entityId,
                    Object before,
                    Object after,
                    String correlationId) {

        AuditLog entry = new AuditLog();
        entry.setAgencyId(agencyId);
        entry.setClientId(clientId);

        // Fall back to tenant context when actor info is not provided explicitly
        if (actorUserId == null) {
            TenantContext ctx = TenantContextHolder.get();
            if (ctx != null) {
                actorUserId = ctx.getUserId();
                actorRole = ctx.getRole();
            }
        }

        entry.setActorUserId(actorUserId);
        entry.setActorRole(actorRole);
        entry.setAction(action.name());
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBeforeJson(toJson(before));
        entry.setAfterJson(toJson(after));
        entry.setCorrelationId(correlationId != null ? correlationId : UUID.randomUUID().toString());
        entry.setCreatedAt(OffsetDateTime.now());

        repository.save(entry);
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise audit payload to JSON", e);
            return "{\"error\":\"serialisation_failed\"}";
        }
    }
}
