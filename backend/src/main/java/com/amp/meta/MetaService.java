package com.amp.meta;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service handling Meta platform connections and sync jobs.
 */
@Service
@Transactional
public class MetaService {

    private final MetaConnectionRepository connectionRepository;
    private final MetaSyncJobRepository syncJobRepository;
    private final AuditService auditService;

    public MetaService(MetaConnectionRepository connectionRepository,
                       MetaSyncJobRepository syncJobRepository,
                       AuditService auditService) {
        this.connectionRepository = connectionRepository;
        this.syncJobRepository = syncJobRepository;
        this.auditService = auditService;
    }

    // ──────── Connection ────────

    @Transactional(readOnly = true)
    public MetaConnectionResponse getConnection(UUID agencyId, UUID clientId) {
        return connectionRepository.findByAgencyIdAndClientId(agencyId, clientId)
                .map(MetaConnectionResponse::from)
                .orElse(null);
    }

    /**
     * Starts the Meta OAuth connect flow (stub).
     * Creates a PENDING connection and returns a placeholder authorization URL.
     */
    public ConnectStartResponse connectStart(UUID agencyId, UUID clientId) {
        TenantContext ctx = TenantContextHolder.require();

        String state = UUID.randomUUID().toString();

        MetaConnection conn = new MetaConnection();
        conn.setAgencyId(agencyId);
        conn.setClientId(clientId);
        conn.setAdAccountId("pending-" + state);
        conn.setAccessTokenEnc(new byte[0]);
        conn.setTokenKeyId("pending");
        conn.setStatus("PENDING");
        conn.setConnectedAt(OffsetDateTime.now());
        conn.setCreatedAt(OffsetDateTime.now());
        conn.setUpdatedAt(OffsetDateTime.now());

        MetaConnection saved = connectionRepository.save(conn);

        auditService.log(agencyId, clientId, ctx.getUserId(), ctx.getRole(),
                AuditAction.META_CONNECT, "MetaConnection", saved.getId(),
                null, saved.getStatus(), null);

        String authorizationUrl = "https://www.facebook.com/v19.0/dialog/oauth?client_id=PLACEHOLDER&state=" + state;
        return new ConnectStartResponse(authorizationUrl, state);
    }

    /**
     * Disconnects the Meta integration for a client.
     */
    public void disconnect(UUID agencyId, UUID clientId) {
        TenantContext ctx = TenantContextHolder.require();

        MetaConnection conn = connectionRepository.findByAgencyIdAndClientId(agencyId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("MetaConnection",
                        clientId));

        String before = conn.getStatus();
        conn.setStatus("DISCONNECTED");
        connectionRepository.save(conn);

        auditService.log(agencyId, clientId, ctx.getUserId(), ctx.getRole(),
                AuditAction.META_DISCONNECT, "MetaConnection", conn.getId(),
                before, "DISCONNECTED", null);
    }

    // ──────── Sync Jobs ────────

    @Transactional(readOnly = true)
    public MetaSyncJobResponse getSyncStatus(UUID agencyId, UUID clientId) {
        return syncJobRepository.findFirstByClientIdOrderByRequestedAtDesc(clientId)
                .map(MetaSyncJobResponse::from)
                .orElse(null);
    }

    /**
     * Creates a new sync job (stub — real implementation will use SQS worker).
     */
    public MetaSyncJobResponse triggerSync(UUID agencyId, UUID clientId, String jobType) {
        TenantContext ctx = TenantContextHolder.require();

        String idempotencyKey = agencyId + ":" + clientId + ":" + jobType + ":"
                + java.time.LocalDate.now();

        MetaSyncJob job = new MetaSyncJob();
        job.setAgencyId(agencyId);
        job.setClientId(clientId);
        job.setJobType(jobType);
        job.setJobStatus("PENDING");
        job.setIdempotencyKey(idempotencyKey);
        job.setRequestedAt(OffsetDateTime.now());

        MetaSyncJob saved = syncJobRepository.save(job);
        return MetaSyncJobResponse.from(saved);
    }
}
