package com.amp.meta;

import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for Meta platform integration.
 */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

    private static final Logger log = LoggerFactory.getLogger(MetaController.class);

    private final MetaService metaService;
    private final MetaGraphApiClient metaGraphApiClient;
    private final MetaSyncService metaSyncService;

    public MetaController(MetaService metaService, MetaGraphApiClient metaGraphApiClient,
                          MetaSyncService metaSyncService) {
        this.metaService = metaService;
        this.metaGraphApiClient = metaGraphApiClient;
        this.metaSyncService = metaSyncService;
    }

    // ──────── helper ────────

    private UUID agencyId() {
        return TenantContextHolder.require().getAgencyId();
    }

    // ──────── Connect / Disconnect ────────

    @PostMapping("/clients/{clientId}/meta/connect/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectStartResponse connectStart(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyRole();
        return metaService.connectStart(agencyId(), clientId);
    }

    @PostMapping("/clients/{clientId}/meta/reconnect/start")
    public ResponseEntity<?> reconnectStart(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyRole();
        // Same as connect — starts a new OAuth flow
        ConnectStartResponse response = metaService.connectStart(agencyId(), clientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/clients/{clientId}/meta/connection")
    public MetaConnectionResponse getConnection(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyRole();
        return metaService.getConnection(agencyId(), clientId);
    }

    @PostMapping("/clients/{clientId}/meta/disconnect")
    public void disconnect(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyRole();
        metaService.disconnect(agencyId(), clientId);
    }

    // ──────── Manual Token Connect ────────

    /**
     * POST /api/v1/clients/{clientId}/meta/connect/manual
     * Connects a client using a manually-pasted Graph API Explorer token.
     * Validates the token, fetches ad accounts / pages, exchanges for long-lived
     * token, and creates the connection.
     */
    @PostMapping("/clients/{clientId}/meta/connect/manual")
    public ResponseEntity<?> connectManual(
            @PathVariable UUID clientId,
            @RequestBody ManualConnectRequest request) {
        RoleGuard.requireAgencyRole();

        try {
            // Validate token by fetching ad accounts
            var adAccounts = metaGraphApiClient.getAdAccounts(request.accessToken());
            if (adAccounts.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("code", "NO_AD_ACCOUNTS",
                                "message", "No ad accounts found for this token"));
            }

            // Find matching ad account if specified, otherwise use first
            MetaGraphApiClient.AdAccountInfo selectedAccount;
            if (request.adAccountId() != null && !request.adAccountId().isBlank()) {
                selectedAccount = adAccounts.stream()
                        .filter(a -> a.id().equals(request.adAccountId())
                                || a.accountId().equals(request.adAccountId()))
                        .findFirst()
                        .orElse(adAccounts.get(0));
            } else {
                selectedAccount = adAccounts.get(0);
            }

            // Get pages
            var pages = metaGraphApiClient.getPages(request.accessToken());
            String pageId = request.pageId();
            if (pageId == null && !pages.isEmpty()) {
                pageId = pages.get(0).id();
            }

            // Try to exchange for long-lived token
            String tokenToStore = request.accessToken();
            try {
                var longLived = metaGraphApiClient.exchangeForLongLivedToken(request.accessToken());
                tokenToStore = longLived.accessToken();
            } catch (Exception e) {
                // Token might already be long-lived, continue with original
                log.info("Could not exchange for long-lived token, using original: {}", e.getMessage());
            }

            // Save connection
            MetaConnection conn = metaService.completeOAuthConnect(
                    agencyId(), clientId, tokenToStore,
                    selectedAccount.id(), request.pixelId(), pageId
            );

            return ResponseEntity.ok(Map.of(
                    "connection", MetaConnectionResponse.from(conn),
                    "adAccounts", adAccounts,
                    "pages", pages
            ));
        } catch (Exception e) {
            log.error("Manual connect failed for client {}: {}", clientId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "CONNECT_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/meta/validate-token
     * Validates a token and returns the ad accounts / pages it has access to.
     */
    @PostMapping("/meta/validate-token")
    public ResponseEntity<?> validateToken(@RequestBody Map<String, String> request) {
        RoleGuard.requireAgencyRole();
        String token = request.get("accessToken");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token is required"));
        }
        try {
            var adAccounts = metaGraphApiClient.getAdAccounts(token);
            var pages = metaGraphApiClient.getPages(token);
            return ResponseEntity.ok(Map.of("adAccounts", adAccounts, "pages", pages));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_TOKEN",
                            "message", "Token validation failed: " + e.getMessage()));
        }
    }

    // ──────── OAuth Callback ────────

    /**
     * GET /api/v1/meta/oauth/callback — Meta redirects here after user authorizes.
     * This is a public endpoint (no JWT needed since it's a redirect from Meta).
     * Returns HTML that communicates with the opener window via postMessage.
     */
    @GetMapping("/meta/oauth/callback")
    public ResponseEntity<String> oauthCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription) {

        if (error != null) {
            log.error("Meta OAuth error: {} - {}", error, errorDescription);
            String html = buildCallbackHtml(false, "Meta authorization failed: " + errorDescription, null);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
        }

        try {
            // Decode state to get agencyId and clientId
            String decoded = new String(Base64.getUrlDecoder().decode(state));
            String[] parts = decoded.split(":");
            UUID agencyId = UUID.fromString(parts[0]);
            UUID clientId = UUID.fromString(parts[1]);

            // Exchange code for short-lived token
            var tokenResult = metaGraphApiClient.exchangeCodeForToken(code);

            // Exchange for long-lived token (60 days)
            var longLivedResult = metaGraphApiClient.exchangeForLongLivedToken(tokenResult.accessToken());

            // Get available ad accounts
            var adAccounts = metaGraphApiClient.getAdAccounts(longLivedResult.accessToken());

            if (adAccounts.isEmpty()) {
                String html = buildCallbackHtml(false, "No ad accounts found for this Facebook user", null);
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
            }

            // Auto-select first ad account for MVP
            var selectedAccount = adAccounts.get(0);

            // Get pages
            var pages = metaGraphApiClient.getPages(longLivedResult.accessToken());
            String pageId = pages.isEmpty() ? null : pages.get(0).id();

            // Save connection
            metaService.completeOAuthConnect(
                    agencyId, clientId,
                    longLivedResult.accessToken(),
                    selectedAccount.id(),
                    null, // pixel — can be set later
                    pageId
            );

            String html = buildCallbackHtml(true, "Successfully connected to Meta!", selectedAccount.name());
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);

        } catch (Exception e) {
            log.error("Meta OAuth callback error", e);
            String html = buildCallbackHtml(false, "Connection failed: " + e.getMessage(), null);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
        }
    }

    /**
     * GET /api/v1/meta/oauth/accounts — list available ad accounts for a token.
     * (Alternative flow if manual account selection is needed instead of auto-select.)
     */
    @GetMapping("/meta/oauth/accounts")
    public ResponseEntity<?> getAvailableAccounts(@RequestParam String accessToken) {
        var accounts = metaGraphApiClient.getAdAccounts(accessToken);
        return ResponseEntity.ok(accounts);
    }

    // ──────── Sync ────────

    @PostMapping("/clients/{clientId}/meta/sync/initial")
    public ResponseEntity<?> triggerInitialSync(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyRole();
        try {
            MetaSyncJob job = metaSyncService.runInitialSync(agencyId(), clientId);
            return ResponseEntity.ok(MetaSyncJobResponse.from(job));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "SYNC_FAILED", "message", e.getMessage()));
        }
    }

    @PostMapping("/clients/{clientId}/meta/sync/daily")
    public ResponseEntity<?> triggerDailySync(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyRole();
        try {
            MetaSyncJob job = metaSyncService.runDailySync(agencyId(), clientId);
            return ResponseEntity.ok(MetaSyncJobResponse.from(job));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "SYNC_FAILED", "message", e.getMessage()));
        }
    }

    @PostMapping("/clients/{clientId}/meta/sync/manual")
    public ResponseEntity<?> triggerManualSync(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyRole();
        try {
            MetaSyncJob job = metaSyncService.runManualSync(agencyId(), clientId);
            return ResponseEntity.ok(MetaSyncJobResponse.from(job));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "SYNC_FAILED", "message", e.getMessage()));
        }
    }

    @GetMapping("/clients/{clientId}/meta/sync/status")
    public ResponseEntity<List<MetaSyncJobResponse>> getSyncStatus(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyRole();
        List<MetaSyncJobResponse> jobs = metaSyncService.getRecentJobs(agencyId(), clientId);
        return ResponseEntity.ok(jobs);
    }

    // ──────── HTML helpers ────────

    private String buildCallbackHtml(boolean success, String message, String accountName) {
        String escapedMessage = message.replace("'", "\\'");
        return """
                <!DOCTYPE html>
                <html>
                <head><title>Meta Connection</title></head>
                <body>
                <h2>%s</h2>
                <p>%s</p>
                %s
                <script>
                if (window.opener) {
                    window.opener.postMessage(
                        { type: 'META_OAUTH_RESULT', success: %s, message: '%s' }, '*'
                    );
                    setTimeout(() => window.close(), 2000);
                }
                </script>
                </body>
                </html>
                """.formatted(
                success ? "Connected!" : "Connection Failed",
                message,
                accountName != null ? "<p>Ad Account: " + accountName + "</p>" : "",
                success,
                escapedMessage
        );
    }
}
