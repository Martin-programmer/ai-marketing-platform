package com.amp.meta;

import com.amp.ai.AiSuggestion;
import com.amp.ai.AiSuggestionRepository;
import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.common.EmailProperties;
import com.amp.common.NotificationHelper;
import com.amp.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
    private final MetaGraphApiClient metaGraphApiClient;
    private final MetaPendingOAuthConnectionService pendingOAuthConnectionService;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final ObjectMapper objectMapper;

    public MetaService(MetaConnectionRepository connectionRepository,
                       MetaSyncJobRepository syncJobRepository,
                       AuditService auditService,
                       MetaProperties metaProps,
                       NotificationHelper notificationHelper,
                       EmailProperties emailProperties,
                       ClientRepository clientRepository,
                       MetaGraphApiClient metaGraphApiClient,
                       MetaPendingOAuthConnectionService pendingOAuthConnectionService,
                       AiSuggestionRepository aiSuggestionRepository,
                       ObjectMapper objectMapper) {
        this.connectionRepository = connectionRepository;
        this.syncJobRepository = syncJobRepository;
        this.auditService = auditService;
        this.metaProps = metaProps;
        this.notificationHelper = notificationHelper;
        this.emailProperties = emailProperties;
        this.clientRepository = clientRepository;
        this.metaGraphApiClient = metaGraphApiClient;
        this.pendingOAuthConnectionService = pendingOAuthConnectionService;
        this.aiSuggestionRepository = aiSuggestionRepository;
        this.objectMapper = objectMapper;
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
        return completeOAuthConnect(agencyId, clientId, accessToken, adAccountId, pixelId, pageId, null);
    }

    public MetaConnection completeOAuthConnect(UUID agencyId, UUID clientId,
                                               String accessToken, String adAccountId,
                                               String pixelId, String pageId,
                                               Long expiresInSeconds) {
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
        conn.setTokenExpiresAt(calculateTokenExpiry(expiresInSeconds));
        conn.setLastTokenRefreshAt(OffsetDateTime.now());
        conn.setTokenRefreshFailed(false);
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

    public void storePendingOAuthToken(UUID agencyId, UUID clientId, String accessToken, OffsetDateTime tokenExpiresAt) {
        pendingOAuthConnectionService.store(agencyId, clientId, accessToken, tokenExpiresAt);
    }

    public Map<String, Object> completeOAuthConnectWithSelectedAccount(UUID agencyId, UUID clientId, String adAccountId) {
        MetaPendingOAuthConnectionService.PendingMetaOAuthConnection pending = pendingOAuthConnectionService.getRequired(clientId);
        if (!agencyId.equals(pending.agencyId()) || !clientId.equals(pending.clientId())) {
            throw new IllegalStateException("Meta authorization does not match this client. Please reconnect.");
        }

        List<MetaGraphApiClient.AdAccountInfo> adAccounts = metaGraphApiClient.getAdAccounts(pending.accessToken());
        MetaGraphApiClient.AdAccountInfo selectedAccount = adAccounts.stream()
                .filter(account -> account.id().equals(adAccountId) || account.accountId().equals(adAccountId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Selected ad account is not available for this authorization."));

        if (!MetaGraphApiClient.isSelectableAdAccount(selectedAccount)) {
            throw new IllegalStateException("Selected ad account is disabled and cannot be connected.");
        }

        List<MetaGraphApiClient.PageInfo> pages = metaGraphApiClient.getPages(pending.accessToken());
        String pageId = pages.isEmpty() ? null : pages.get(0).id();

        List<MetaGraphApiClient.PixelInfo> pixels = metaGraphApiClient.getAdAccountPixels(pending.accessToken(), selectedAccount.id());
        String pixelId = pixels.isEmpty() ? null : pixels.get(0).id();

        MetaConnection connection = completeOAuthConnect(
                agencyId,
                clientId,
                pending.accessToken(),
                selectedAccount.id(),
                pixelId,
            pageId,
            expiresInSecondsFrom(pending.tokenExpiresAt())
        );

        pendingOAuthConnectionService.clear(clientId);

        return Map.of(
                "connection", MetaConnectionResponse.from(connection),
                "adAccount", Map.of(
                        "id", selectedAccount.id(),
                        "name", selectedAccount.name(),
                        "accountId", selectedAccount.accountId(),
                        "currency", selectedAccount.currency(),
                        "timezone", selectedAccount.timezone(),
                        "status", selectedAccount.status(),
                        "statusLabel", MetaGraphApiClient.adAccountStatusLabel(selectedAccount.status())
                )
        );
    }

    /**
     * Decrypt and return the access token for a connection.
     * MVP: stored as plain bytes. TODO: KMS decrypt.
     */
    public String getAccessToken(MetaConnection conn) {
        return new String(conn.getAccessTokenEnc(), StandardCharsets.UTF_8);
    }

    /**
     * Get a connected MetaConnection for a client, or throw.
     */
    @Transactional(readOnly = true)
    public MetaConnection getConnectionOrThrow(UUID agencyId, UUID clientId) {
        return connectionRepository.findByAgencyIdAndClientId(agencyId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("MetaConnection",
                        "No Meta connection found for client " + clientId));
    }

    public TokenRefreshResult refreshExpiringConnections() {
        OffsetDateTime threshold = OffsetDateTime.now().plusDays(7);
        List<MetaConnection> connections = connectionRepository.findExpiringConnections(List.of("CONNECTED"), threshold);
        int refreshed = 0;
        int failed = 0;

        for (MetaConnection conn : connections) {
            try {
                MetaGraphApiClient.TokenExchangeResult refreshedToken = metaGraphApiClient.exchangeForLongLivedToken(getAccessToken(conn));
                conn.setAccessTokenEnc(refreshedToken.accessToken().getBytes(StandardCharsets.UTF_8));
                conn.setTokenKeyId("plaintext-mvp");
                conn.setTokenExpiresAt(calculateTokenExpiry(refreshedToken.expiresInSeconds()));
                conn.setLastTokenRefreshAt(OffsetDateTime.now());
                conn.setTokenRefreshFailed(false);
                conn.setStatus("CONNECTED");
                clearTokenErrors(conn);
                conn.setUpdatedAt(OffsetDateTime.now());
                connectionRepository.save(conn);
                refreshed++;

                TenantContext ctx = TenantContextHolder.get();
                if (ctx != null) {
                    auditService.log(conn.getAgencyId(), conn.getClientId(), ctx.getUserId(), ctx.getRole(),
                            AuditAction.META_TOKEN_REFRESH, "MetaConnection", conn.getId(),
                            null, "CONNECTED", null);
                }
            } catch (Exception e) {
                failed++;
                if (isAuthError(e)) {
                    markTokenExpired(conn, e.getMessage(), true);
                } else {
                    markTokenRefreshFailed(conn, e.getMessage(), true);
                }
            }
        }

        return new TokenRefreshResult(refreshed, failed, connections.size());
    }

    public void markTokenRefreshFailed(MetaConnection conn, String message, boolean createDiagnosticSuggestion) {
        conn.setTokenRefreshFailed(true);
        conn.setLastErrorCode("TOKEN_REFRESH_FAILED");
        conn.setLastErrorMessage(message);
        conn.setUpdatedAt(OffsetDateTime.now());
        connectionRepository.save(conn);

        if (createDiagnosticSuggestion) {
            createMetaDiagnosticSuggestion(conn.getAgencyId(), conn.getClientId(), "TOKEN_REFRESH_FAILED", message);
        }
        sendMetaAttentionAlert(
                conn.getAgencyId(),
                conn.getClientId(),
                "Meta Token Refresh Failed — ",
                "Meta Token Refresh Failed",
                "Meta token refresh failed. Ads may stop syncing soon. Error: " + message,
                "HIGH"
        );
    }

    public void markTokenExpired(MetaConnection conn, String message, boolean createDiagnosticSuggestion) {
        String before = conn.getStatus();
        conn.setStatus("TOKEN_EXPIRED");
        conn.setTokenRefreshFailed(true);
        conn.setLastErrorCode("TOKEN_EXPIRED");
        conn.setLastErrorMessage(message);
        conn.setUpdatedAt(OffsetDateTime.now());
        connectionRepository.save(conn);

        TenantContext ctx = TenantContextHolder.get();
        if (ctx != null) {
            auditService.log(conn.getAgencyId(), conn.getClientId(), ctx.getUserId(), ctx.getRole(),
                    AuditAction.META_TOKEN_EXPIRED, "MetaConnection", conn.getId(),
                    before, "TOKEN_EXPIRED", null);
        }

        if (createDiagnosticSuggestion) {
            createMetaDiagnosticSuggestion(conn.getAgencyId(), conn.getClientId(), "TOKEN_EXPIRED", message);
        }
        sendMetaAttentionAlert(
                conn.getAgencyId(),
                conn.getClientId(),
                "Meta Token Expired — ",
                "Meta Connection Requires Reconnect",
                "Meta token expired or was rejected. Please reconnect this client before the next sync. Error: " + message,
                "HIGH"
        );
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
        conn.setTokenExpiresAt(null);
        conn.setLastTokenRefreshAt(null);
        conn.setTokenRefreshFailed(false);
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

    private OffsetDateTime calculateTokenExpiry(Long expiresInSeconds) {
        if (expiresInSeconds == null || expiresInSeconds <= 0) {
            return null;
        }
        return OffsetDateTime.now().plusSeconds(expiresInSeconds);
    }

    private Long expiresInSecondsFrom(OffsetDateTime tokenExpiresAt) {
        if (tokenExpiresAt == null) {
            return null;
        }
        long seconds = java.time.Duration.between(OffsetDateTime.now(), tokenExpiresAt).getSeconds();
        return Math.max(seconds, 0);
    }

    private void clearTokenErrors(MetaConnection conn) {
        if ("TOKEN_REFRESH_FAILED".equals(conn.getLastErrorCode()) || "TOKEN_EXPIRED".equals(conn.getLastErrorCode())) {
            conn.setLastErrorCode(null);
            conn.setLastErrorMessage(null);
        }
    }

    private boolean isAuthError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("oauth")
                || normalized.contains("error validating access token")
                || normalized.contains("code 190")
                || normalized.contains("190");
    }

    private void createMetaDiagnosticSuggestion(UUID agencyId, UUID clientId, String diagnosticCode, String message) {
        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setAgencyId(agencyId);
        suggestion.setClientId(clientId);
        suggestion.setScopeType("CLIENT");
        suggestion.setScopeId(clientId);
        suggestion.setSuggestionType("DIAGNOSTIC");
        suggestion.setPayloadJson(toPayloadJson(Map.of(
                "diagnosticCode", diagnosticCode,
                "source", "META_CONNECTION",
                "message", message
        )));
        suggestion.setRationale("Meta connection issue detected: " + message);
        suggestion.setConfidence(new BigDecimal("0.990"));
        suggestion.setRiskLevel("HIGH");
        suggestion.setStatus("PENDING");
        suggestion.setCreatedBy("system");
        suggestion.setCreatedAt(OffsetDateTime.now());
        aiSuggestionRepository.save(suggestion);
    }

    private String toPayloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private void sendMetaAttentionAlert(UUID agencyId,
                                        UUID clientId,
                                        String subjectPrefix,
                                        String alertTitle,
                                        String alertMessage,
                                        String severity) {
        try {
            String clientName = clientRepository.findByIdAndAgencyId(clientId, agencyId)
                    .map(Client::getName).orElse("Client");
            String dashboardLink = emailProperties.getBaseUrl() + "/clients";
            java.util.List<String> admins = notificationHelper.getAgencyAdminEmails(agencyId);
            for (String email : admins) {
                notificationHelper.sendTemplatedAsync(email,
                        subjectPrefix + clientName,
                        "alert",
                        java.util.Map.of(
                                "alertTitle", alertTitle,
                                "alertMessage", alertMessage,
                                "clientName", clientName,
                                "severity", severity,
                                "severityColor", "HIGH".equals(severity) ? "#D32F2F" : "#ED6C02",
                                "dashboardLink", dashboardLink
                        ));
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(MetaService.class)
                    .warn("Failed to send Meta alert: {}", e.getMessage());
        }
    }

    public record TokenRefreshResult(int refreshed, int failed, int considered) {
    }
}
