package com.amp.ai;

import com.amp.agency.Agency;
import com.amp.agency.AgencyRepository;
import com.amp.clients.Client;
import com.amp.clients.ClientRepository;
import com.amp.common.RoleGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api/v1/owner/ai-audit")
public class OwnerAiAuditController {

    private final AiPromptLogRepository logRepo;
    private final AgencyRepository agencyRepo;
    private final ClientRepository clientRepo;

    public OwnerAiAuditController(AiPromptLogRepository logRepo,
                                   AgencyRepository agencyRepo,
                                   ClientRepository clientRepo) {
        this.logRepo = logRepo;
        this.agencyRepo = agencyRepo;
        this.clientRepo = clientRepo;
    }

    /* ---------- paginated log list ---------- */

    @PostMapping("/logs")
    public ResponseEntity<?> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) UUID agencyId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean success) {
        RoleGuard.requireOwnerAdmin();

        OffsetDateTime fromDt = from != null ? from.atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now().minusDays(30);
        OffsetDateTime toDt = to != null ? to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now().plusDays(1);

        Page<AiPromptLog> logs = logRepo.findAuditLogs(fromDt, toDt, agencyId, module, success,
                PageRequest.of(page, Math.min(size, 100)));

        Map<UUID, String> agencyNames = loadAgencyNames();

        List<Map<String, Object>> rows = logs.getContent().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("agencyId", l.getAgencyId());
            m.put("agencyName", agencyNames.getOrDefault(l.getAgencyId(), "—"));
            m.put("clientId", l.getClientId());
            m.put("module", l.getModule());
            m.put("model", l.getModel());
            m.put("promptTokens", l.getPromptTokens());
            m.put("completionTokens", l.getCompletionTokens());
            m.put("totalTokens", l.getTotalTokens());
            m.put("costUsd", l.getCostUsd());
            m.put("durationMs", l.getDurationMs());
            m.put("success", l.isSuccess());
            m.put("errorMessage", l.getErrorMessage());
            m.put("createdAt", l.getCreatedAt());
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of(
            "content", rows,
            "page", logs.getNumber(),
            "size", logs.getSize(),
            "totalElements", logs.getTotalElements(),
            "totalPages", logs.getTotalPages()
        ));
    }

    /* ---------- aggregated summary ---------- */

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RoleGuard.requireOwnerAdmin();

        OffsetDateTime fromDt = from != null ? from.atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now().minusDays(30);
        OffsetDateTime toDt = to != null ? to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now().plusDays(1);

        return ResponseEntity.ok(buildSummary(fromDt, toDt, null));
    }

    /* ---------- single agency detail ---------- */

    @GetMapping("/agency/{agencyId}")
    public ResponseEntity<?> getAgencyDetail(
            @PathVariable UUID agencyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RoleGuard.requireOwnerAdmin();

        OffsetDateTime fromDt = from != null ? from.atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now().minusDays(30);
        OffsetDateTime toDt = to != null ? to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now().plusDays(1);

        Map<String, Object> result = buildSummary(fromDt, toDt, agencyId);

        // Add per-client breakdown
        List<Object[]> byClient = logRepo.summaryByClient(fromDt, toDt, agencyId);
        Map<UUID, String> clientNames = loadClientNames(agencyId);
        List<Map<String, Object>> clientList = byClient.stream().map(r -> Map.<String, Object>of(
            "clientId", r[0] != null ? r[0] : "unknown",
            "clientName", r[0] != null ? clientNames.getOrDefault((UUID) r[0], "—") : "—",
            "calls", ((Number) r[1]).longValue(),
            "tokens", ((Number) r[2]).longValue(),
            "costUsd", r[3]
        )).toList();
        result.put("byClient", clientList);

        return ResponseEntity.ok(result);
    }

    /* ---------- single log entry ---------- */

    @GetMapping("/log/{logId}")
    public ResponseEntity<?> getLogDetail(@PathVariable UUID logId) {
        RoleGuard.requireOwnerAdmin();

        AiPromptLog l = logRepo.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("Log not found"));

        Map<UUID, String> agencyNames = loadAgencyNames();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("agencyId", l.getAgencyId());
        m.put("agencyName", agencyNames.getOrDefault(l.getAgencyId(), "—"));
        m.put("clientId", l.getClientId());
        m.put("module", l.getModule());
        m.put("model", l.getModel());
        m.put("promptTokens", l.getPromptTokens());
        m.put("completionTokens", l.getCompletionTokens());
        m.put("totalTokens", l.getTotalTokens());
        m.put("costUsd", l.getCostUsd());
        m.put("durationMs", l.getDurationMs());
        m.put("success", l.isSuccess());
        m.put("errorMessage", l.getErrorMessage());
        m.put("inputText", l.getInputText());
        m.put("outputText", l.getOutputText());
        m.put("createdAt", l.getCreatedAt());

        return ResponseEntity.ok(m);
    }

    /* ---------- helpers ---------- */

    private Map<String, Object> buildSummary(OffsetDateTime from, OffsetDateTime to, UUID agencyId) {
        Object[] stats = logRepo.summaryStats(from, to, agencyId).stream()
            .findFirst()
            .orElseGet(() -> new Object[]{0L, 0L, 0L, 0L, 0L, 0L, BigDecimal.ZERO, 0D});

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCalls", ((Number) stats[0]).longValue());
        result.put("successfulCalls", ((Number) stats[1]).longValue());
        result.put("failedCalls", ((Number) stats[2]).longValue());
        result.put("totalTokens", ((Number) stats[3]).longValue());
        result.put("totalPromptTokens", ((Number) stats[4]).longValue());
        result.put("totalCompletionTokens", ((Number) stats[5]).longValue());
        result.put("totalCostUsd", stats[6] instanceof BigDecimal bd ? bd : BigDecimal.ZERO);
        result.put("avgDurationMs", ((Number) stats[7]).doubleValue());

        // byModule
        List<Object[]> byModule = logRepo.summaryByModule(from, to, agencyId);
        result.put("byModule", byModule.stream().map(r -> Map.of(
            "module", r[0],
            "calls", ((Number) r[1]).longValue(),
            "tokens", ((Number) r[2]).longValue(),
            "costUsd", r[3]
        )).toList());

        // byAgency
        List<Object[]> byAgency = logRepo.summaryByAgency(from, to, agencyId);
        Map<UUID, String> agencyNames = loadAgencyNames();
        result.put("byAgency", byAgency.stream().map(r -> Map.of(
            "agencyId", r[0] != null ? r[0] : "unknown",
            "agencyName", r[0] != null ? agencyNames.getOrDefault((UUID) r[0], "—") : "—",
            "calls", ((Number) r[1]).longValue(),
            "tokens", ((Number) r[2]).longValue(),
            "costUsd", r[3]
        )).toList());

        // byDay
        List<Object[]> byDay = logRepo.summaryByDay(from, to, agencyId);
        result.put("byDay", byDay.stream().map(r -> Map.of(
            "date", r[0].toString(),
            "calls", ((Number) r[1]).longValue(),
            "tokens", ((Number) r[2]).longValue(),
            "costUsd", r[3]
        )).toList());

        return result;
    }

    private Map<UUID, String> loadAgencyNames() {
        Map<UUID, String> map = new HashMap<>();
        for (Agency a : agencyRepo.findAll()) {
            map.put(a.getId(), a.getName());
        }
        return map;
    }

    private Map<UUID, String> loadClientNames(UUID agencyId) {
        Map<UUID, String> map = new HashMap<>();
        for (Client c : clientRepo.findAllByAgencyId(agencyId)) {
            map.put(c.getId(), c.getName());
        }
        return map;
    }
}
