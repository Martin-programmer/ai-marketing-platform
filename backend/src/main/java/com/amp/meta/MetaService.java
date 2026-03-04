package com.amp.meta;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
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
    private final MetaProperties metaProps;

    public MetaService(MetaConnectionRepository connectionRepository,
                       MetaSyncJobRepository syncJobRepository,
                       AuditService auditService,
                       MetaProperties metaProps) {
        this.connectionRepository = connectionRepository;
        this.syncJobRepository = syncJobRepository;
        this.auditService = auditService;
        this.metaProps = metaProps;
    }

    // ──────── Connection ────────

    @Transactional(readOnly = true)
    public MetaConnectionResponse getConnection(UUID agencyId, UUID clientId) {
        return connectionRepository.findByAgencyIdAndClientId(agencyId, clientId)
                .map(MetaConnectionResponse::from)
                .orElse(null);
    }

    /**
     * Starts the Meta OAuth connect flow.
     * Generates a real Meta authorization URL with proper scopes and state.
     * The connection is NOT created here — it will be created in the OAuth callback
     * after successful token exchange.
     */
    public ConnectStartResponse connectStart(UUID agencyId, UUID clientId) {
        // Generate state containing agencyId, clientId, and nonce (Base64-encoded)
        String nonce = UUID.randomUUID().toString();
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (agencyId + ":" + clientId + ":" + nonce).getBytes(StandardCharsets.UTF_8)
        );

        // Build Meta OAuth authorization URL
        String scopes = "ads_management,ads_read,pages_read_engagement,business_management";
        String authorizationUrl = UriComponentsBuilder
                .fromHttpUrl(metaProps.getOauthAuthorizeUrl())
                .queryParam("client_id", metaProps.getAppId())
                .queryParam("redirect_uri", metaProps.getRedirectUri())
                .queryParam("state", state)
                .queryParam("scope", scopes)
                .queryParam("response_type", "code")
                .toUriString();

        return new ConnectStartResponse(authorizationUrl, state);
    }

    /**
     * Completes the OAuth connect flow after successful token exchange.
     * Creates (or replaces) the MetaConnection for the client.
     * <p>
     * MVP: stores access token as plain bytes. TODO: KMS envelope encryption.
     */
    public MetaConnection completeOAuthConnect(UUID agencyId, UUID clientId,
                                               String accessToken, String adAccountId,
                                               String pixelId, String pageId) {
        // Delete any existing connection for this client
        connectionRepository.findByAgencyIdAndClientId(agencyId, clientId)
                .ifPresent(connectionRepository::delete);

        MetaConnection conn = new MetaConnection();
        conn.setAgencyId(agencyId);
        conn.setClientId(clientId);
        conn.setAdAccountId(adAccountId);
        conn.setPixelId(pixelId);
        conn.setPageId(pageId);
        // MVP: store token as plain bytes. TODO: KMS envelope encryption
        conn.setAccessTokenEnc(accessToken.getBytes(StandardCharsets.UTF_8));
        conn.setTokenKeyId("plaintext-mvp");
        conn.setStatus("CONNECTED");
        conn.setConnectedAt(OffsetDateTime.now());
        conn.setCreatedAt(OffsetDateTime.now());
        conn.setUpdatedAt(OffsetDateTime.now());

        MetaConnection saved = connectionRepository.save(conn);

        TenantContext ctx = TenantContextHolder.require();
        auditService.log(agencyId, clientId, ctx.getUserId(), ctx.getRole(),
                AuditAction.META_CONNECT, "MetaConnection", saved.getId(),
                null, saved.getStatus(), null);

        return saved;
    }

    /**
     * Decrypt and return the access token for a connection.
     * MVP: stored as plain bytes. TODO: KMS decrypt.
     */
    public String getAccessToken(MetaConnection conn) {
        return new String(conn.getAccessTokenEnc(), StandardCharsets.UTF_8);
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
