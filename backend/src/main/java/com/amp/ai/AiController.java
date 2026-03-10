package com.amp.ai;

import com.amp.auth.AccessControl;
import com.amp.auth.Permission;
import com.amp.common.RoleGuard;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;
    private final PerformanceOptimizerService optimizerService;
    private final WeeklyDigestService weeklyDigestService;
    private final AiWeeklyDigestRepository digestRepo;
    private final AccessControl accessControl;

    public AiController(ClaudeApiClient claudeClient,
                        AiProperties aiProps,
                        PerformanceOptimizerService optimizerService,
                        WeeklyDigestService weeklyDigestService,
                        AiWeeklyDigestRepository digestRepo,
                        AccessControl accessControl) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.optimizerService = optimizerService;
        this.weeklyDigestService = weeklyDigestService;
        this.digestRepo = digestRepo;
        this.accessControl = accessControl;
    }

    /**
     * Test endpoint to verify Claude API connectivity.
     */
    @PostMapping("/test")
    public ResponseEntity<?> testClaude(@RequestBody Map<String, String> request) {
        RoleGuard.requireOwnerAdmin();
        String message = request.getOrDefault("message", "Say hello in one sentence.");

        var response = claudeClient.sendMessage(
            "You are a helpful assistant. Respond briefly.",
            message, "TEST", null, null
        );

        if (response.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                "response", response.text(),
                "inputTokens", response.inputTokens(),
                "outputTokens", response.outputTokens(),
                "cost", response.cost(),
                "durationMs", response.durationMs()
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "error", response.error()
            ));
        }
    }

    /**
     * Get AI usage stats.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        RoleGuard.requireOwnerAdmin();
        return ResponseEntity.ok(Map.of(
            "configured", aiProps.getAnthropic().getApiKey() != null && !"placeholder".equals(aiProps.getAnthropic().getApiKey()),
            "defaultModel", aiProps.getAnthropic().getDefaultModel(),
            "analyzerEnabled", aiProps.getAnalyzer().isEnabled(),
            "optimizerEnabled", aiProps.getOptimizer().isEnabled()
        ));
    }

    /**
     * Manually trigger optimisation for all connected clients.
     */
    @PostMapping("/optimizer/run")
    public ResponseEntity<?> runOptimizer() {
        RoleGuard.requireAgencyAdmin();
        var result = optimizerService.runForAllClients();
        return ResponseEntity.ok(result);
    }

    /**
     * Run optimisation for a specific client.
     */
    @PostMapping("/optimizer/run/{clientId}")
    public ResponseEntity<?> runOptimizerForClient(@PathVariable UUID clientId) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        var result = optimizerService.runForClient(agencyId, clientId);
        return ResponseEntity.ok(result);
    }

    // ──────── Weekly Digest ────────

    /**
     * Manually trigger weekly digest generation for all connected clients.
     */
    @PostMapping("/digest/generate")
    public ResponseEntity<?> generateDigests() {
        RoleGuard.requireAgencyAdmin();
        var result = weeklyDigestService.generateAndSendDigests();
        return ResponseEntity.ok(result);
    }

    /**
     * List recent digests for a client.
     */
    @GetMapping("/clients/{clientId}/digests")
    public ResponseEntity<List<DigestResponse>> listDigests(@PathVariable UUID clientId) {
        accessControl.requireClientPermission(clientId, Permission.REPORTS_VIEW);
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        List<DigestResponse> digests = digestRepo
                .findAllByAgencyIdAndClientIdOrderByCreatedAtDesc(agencyId, clientId)
                .stream().map(DigestResponse::from).toList();
        return ResponseEntity.ok(digests);
    }

    /**
     * View a specific digest's HTML content.
     */
    @GetMapping("/digests/{digestId}/html")
    public ResponseEntity<String> viewDigestHtml(@PathVariable UUID digestId) {
        RoleGuard.requireAgencyRole();
        UUID agencyId = TenantContextHolder.require().getAgencyId();
        AiWeeklyDigest digest = digestRepo.findByIdAndAgencyId(digestId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Digest", digestId));
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(digest.getHtmlContent());
    }
}
