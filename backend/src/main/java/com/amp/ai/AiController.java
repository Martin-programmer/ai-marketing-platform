package com.amp.ai;

import com.amp.common.RoleGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final ClaudeApiClient claudeClient;
    private final AiProperties aiProps;

    public AiController(ClaudeApiClient claudeClient, AiProperties aiProps) {
        this.claudeClient = claudeClient;
        this.aiProps = aiProps;
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
}
