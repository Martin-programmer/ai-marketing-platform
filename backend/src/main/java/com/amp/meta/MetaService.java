package com.amp.meta;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.common.EmailProperties;
import com.amp.common.NotificationHelper;
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
    private final NotificationHelper notificationHelper;
    private final EmailProperties emailProperties;
    private final ClientRepository clientRepository;

    public MetaService(MetaConnectionRepository connectionRepository,
                       MetaSyncJobRepository syncJobRepository,
                       AuditService auditService,
                       MetaProperties metaProps,
                       NotificationHelper notificationHelper,
                       EmailProperties emailProperties,
                       ClientRepository clientRepository) {
        this.connectionRepository = connectionRepository;
        this.syncJobRepository = syncJobRepository;
        this.auditService = auditService;
        this.metaProps = metaProps;
        this.notificationHelper = notificationHelper;
        this.emailProperties = emailProperties;
        this.clientRepository = clientRepository;
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
        // Upsert existing connection to avoid delete/insert ordering issues on reconnect
        MetaConnection conn = connectionRepository.findByAgencyIdAndClientId(agencyId, clientId)
            .or(() -> connectionRepository.findByClientId(clientId))
            .orElseGet(MetaConnection::new);

        boolean isNew = conn.getId() == null;
        if (isNew) {
            conn.setAgencyId(agencyId);
            conn.setClientId(clientId);
            conn.setCreatedAt(OffsetDateTime.now());
        } else {
            // Keep row identity to satisfy unique(client_id), but normalize tenant linkage.
            conn.setAgencyId(agencyId);
            conn.setClientId(clientId);
        }

        conn.setAdAccountId(adAccountId);
        conn.setPixelId(pixelId);
        conn.setPageId(pageId);
        // MVP: store token as plain bytes. TODO: KMS envelope encryption
        conn.setAccessTokenEnc(accessToken.getBytes(StandardCharsets.UTF_8));
        conn.setTokenKeyId("plaintext-mvp");
        conn.setStatus("CONNECTED");
        conn.setConnectedAt(OffsetDateTime.now());
        conn.setUpdatedAt(OffsetDateTime.now());
        conn.setLastErrorCode(null);
        conn.setLastErrorMessage(null);

        MetaConnection saved = connectionRepository.save(conn);

        // OAuth callback endpoint is public and may not have tenant context.
        // Log audit only when context exists.
        TenantContext ctx = TenantContextHolder.get();
        if (ctx != null) {
            auditService.log(agencyId, clientId, ctx.getUserId(), ctx.getRole(),
                    AuditAction.META_CONNECT, "MetaConnection", saved.getId(),
                    null, saved.getStatus(), null);
        }

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
        // Keep ad_account_id because DB column is NOT NULL.
        // UI should hide account details when status is DISCONNECTED.
        conn.setPixelId(null);
        conn.setPageId(null);
        conn.setLastSyncAt(null);
        conn.setLastErrorCode(null);
        conn.setLastErrorMessage(null);
        conn.setAccessTokenEnc(new byte[0]);
        connectionRepository.save(conn);

        auditService.log(agencyId, clientId, ctx.getUserId(), ctx.getRole(),
                AuditAction.META_DISCONNECT, "MetaConnection", conn.getId(),
                before, "DISCONNECTED", null);

        // Send Meta disconnected alert to AGENCY_ADMINs
        try {
            String clientName = clientRepository.findByIdAndAgencyId(clientId, agencyId)
                    .map(Client::getName).orElse("Client");
            String dashboardLink = emailProperties.getBaseUrl() + "/clients";
            java.util.List<String> admins = notificationHelper.getAgencyAdminEmails(agencyId);
            for (String email : admins) {
                notificationHelper.sendTemplatedAsync(email,
                        "Meta Connection Disconnected — " + clientName,
                        "alert",
                        java.util.Map.of(
                                "alertTitle", "Meta Connection Needs Attention",
                                "alertMessage", "Meta connection for " + clientName + " has been disconnected. Please reconnect to resume ad sync.",
                                "clientName", clientName,
                                "severity", "HIGH",
                                "severityColor", "#D32F2F",
                                "dashboardLink", dashboardLink
                        ));
            }
        } catch (Exception e) {
            // fire-and-forget — don't block disconnect
            org.slf4j.LoggerFactory.getLogger(MetaService.class)
                    .warn("Failed to send Meta disconnect alert: {}", e.getMessage());
        }
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
