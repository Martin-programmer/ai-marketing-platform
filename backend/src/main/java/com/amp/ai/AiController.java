package com.amp.ai;

import com.amp.common.RoleGuard;
import com.amp.tenancy.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;
    private final PerformanceOptimizerService optimizerService;

    public AiController(ClaudeApiClient claudeClient,
                        AiProperties aiProps,
                        PerformanceOptimizerService optimizerService) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
        this.optimizerService = optimizerService;
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
}
