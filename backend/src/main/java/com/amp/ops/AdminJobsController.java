package com.amp.ops;

import com.amp.common.RoleGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminJobsController {

    private final ScheduledJobsService scheduledJobsService;

    public AdminJobsController(ScheduledJobsService scheduledJobsService) {
        this.scheduledJobsService = scheduledJobsService;
    }

    /**
     * Manual trigger for the full daily sync job.
     * OWNER_ADMIN only.
     */
    @PostMapping("/daily-sync")
    public ResponseEntity<?> triggerDailySync() {
        RoleGuard.requireOwnerAdmin();

        ScheduledJobsService.DailySyncResult result = scheduledJobsService.runDailySyncJob();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientsSynced", result.clientsSynced());
        body.put("clientsFailed", result.clientsFailed());
        body.put("suggestionsGenerated", result.suggestionsGenerated());
        body.put("errors", result.errors());

        return ResponseEntity.ok(body);
    }
}
