package com.amp.ops;

import com.amp.ai.PerformanceOptimizerService;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.common.EmailService;
import com.amp.common.NotificationHelper;
import jakarta.annotation.PostConstruct;
import com.amp.meta.MetaConnection;
import com.amp.meta.MetaConnectionRepository;
import com.amp.meta.MetaService;
import com.amp.meta.MetaSyncService;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled operational jobs.
 *
 * EC2 note (SSL renew):
 * 0 0 1 * * certbot renew --quiet && docker-compose -f /home/ec2-user/amp/docker-compose.yml restart frontend
 */
@Service
public class ScheduledJobsService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobsService.class);
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Value("${ops.daily-sync.cron:0 30 3 * * *}")
    private String cronExpression;

    @Value("${ops.meta-token-refresh.cron:0 0 2 * * *}")
    private String metaTokenRefreshCronExpression;

    private final MetaConnectionRepository metaConnectionRepository;
    private final MetaService metaService;
    private final MetaSyncService metaSyncService;
    private final PerformanceOptimizerService performanceOptimizerService;
    private final NotificationHelper notificationHelper;
    private final EmailService emailService;
    private final ClientRepository clientRepository;

    public ScheduledJobsService(MetaConnectionRepository metaConnectionRepository,
                                MetaService metaService,
                                MetaSyncService metaSyncService,
                                PerformanceOptimizerService performanceOptimizerService,
                                NotificationHelper notificationHelper,
                                EmailService emailService,
                                ClientRepository clientRepository) {
        this.metaConnectionRepository = metaConnectionRepository;
        this.metaService = metaService;
        this.metaSyncService = metaSyncService;
        this.performanceOptimizerService = performanceOptimizerService;
        this.notificationHelper = notificationHelper;
        this.emailService = emailService;
        this.clientRepository = clientRepository;
    }

    @PostConstruct
    public void onInit() {
        log.info("ScheduledJobsService initialized, daily sync enabled");
        log.info("ScheduledJobsService daily sync cron: {}", cronExpression);
        log.info("ScheduledJobsService token refresh cron: {}", metaTokenRefreshCronExpression);
    }

    @Scheduled(cron = "${ops.meta-token-refresh.cron:0 0 2 * * *}", zone = "Europe/Sofia")
    public void refreshMetaTokens() {
        log.info("Meta token refresh job started (Europe/Sofia 02:00 schedule)");
        MetaService.TokenRefreshResult result = metaService.refreshExpiringConnections();
        log.info("Meta token refresh complete: {} refreshed, {} failed, {} considered",
                result.refreshed(), result.failed(), result.considered());
    }

    /**
     * Daily Meta sync at 03:30 Europe/Sofia.
     */
    @Scheduled(cron = "${ops.daily-sync.cron:0 30 3 * * *}", zone = "Europe/Sofia")
    public void dailySync() {
        runDailySyncJob();
    }

    /**
     * Runs the full daily sync job and returns summary.
     */
    public DailySyncResult runDailySyncJob() {
        log.info("Daily sync job started (Europe/Sofia 03:30 schedule)");

        List<MetaConnection> connected = metaConnectionRepository.findByStatus("CONNECTED");
        int clientsSynced = 0;
        int clientsFailed = 0;
        int suggestionsGenerated = 0;
        List<String> errors = new ArrayList<>();

        for (MetaConnection conn : connected) {
            UUID agencyId = conn.getAgencyId();
            UUID clientId = conn.getClientId();

            try {
                TenantContextHolder.set(systemTenantContext(agencyId, clientId));

                // Uses existing logic (includes anomaly detection inside MetaSyncService).
                metaSyncService.runDailySync(agencyId, clientId);
                clientsSynced++;
            } catch (Exception e) {
                clientsFailed++;
                String error = "Client " + clientId + ": " + e.getMessage();
                errors.add(error);
                log.error("Daily sync failed for client {}: {}", clientId, e.getMessage(), e);

                sendSyncFailureAlert(agencyId, clientId, e.getMessage());
            } finally {
                TenantContextHolder.clear();
            }
        }

        // Run optimizer only if at least one sync succeeded
        if (clientsSynced > 0) {
            try {
                Map<String, Object> optimizerResult = performanceOptimizerService.runForAllClients();
                Object totalSuggestions = optimizerResult.get("totalSuggestions");
                if (totalSuggestions instanceof Integer s) {
                    suggestionsGenerated = s;
                }
            } catch (Exception e) {
                String error = "Optimizer: " + e.getMessage();
                errors.add(error);
                log.error("Daily optimizer run failed: {}", e.getMessage(), e);
            }
        } else {
            log.info("Skipping optimizer run because no client sync succeeded");
        }

        log.info("Daily sync complete: {} clients synced, {} failed, {} suggestions generated",
                clientsSynced, clientsFailed, suggestionsGenerated);

        return new DailySyncResult(clientsSynced, clientsFailed, suggestionsGenerated, errors);
    }

    private void sendSyncFailureAlert(UUID agencyId, UUID clientId, String errorMessage) {
        try {
            String clientName = clientRepository.findByIdAndAgencyId(clientId, agencyId)
                    .map(Client::getName)
                    .orElse("Client");

            List<String> admins = notificationHelper.getAgencyAdminEmails(agencyId);
            if (admins.isEmpty()) return;

            String subject = "Daily sync failed for " + clientName;
            String body = """
                    <h3 style=\"margin-top:0;color:#D32F2F;\">Daily Sync Failure Alert</h3>
                    <p>Meta daily sync failed for <strong>%s</strong>.</p>
                    <p><strong>Error:</strong> %s</p>
                    <p>Please check Meta connection status and retry sync from the dashboard.</p>
                    """.formatted(clientName, escapeHtml(errorMessage));

            for (String email : admins) {
                try {
                    emailService.sendEmail(email, subject, body);
                } catch (Exception ex) {
                    log.warn("Failed sending sync failure alert to {}: {}", email, ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not prepare sync failure alert for agency {} client {}: {}",
                    agencyId, clientId, e.getMessage());
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private TenantContext systemTenantContext(UUID agencyId, UUID clientId) {
        return new TenantContext(agencyId, SYSTEM_USER_ID, "system@scheduled-job.local", "SYSTEM", clientId);
    }

    public record DailySyncResult(
            int clientsSynced,
            int clientsFailed,
            int suggestionsGenerated,
            List<String> errors
    ) {}
}
