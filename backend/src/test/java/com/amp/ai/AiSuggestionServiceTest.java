package com.amp.ai;

import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceTest {

    private static final UUID AGENCY_ID     = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID     = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID USER_ID       = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID SUGGESTION_ID = UUID.randomUUID();

    @Mock private AiSuggestionRepository suggestionRepository;
    @Mock private AiActionLogRepository actionLogRepository;
    @Mock private AuditService auditService;
    @Mock private ExecutorService executorService;

    @InjectMocks
    private AiSuggestionService suggestionService;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.set(new TenantContext(AGENCY_ID, USER_ID, "test@local", "AGENCY_ADMIN"));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // ──────── helpers ────────

    private AiSuggestion buildSuggestion(String status) {
        AiSuggestion s = new AiSuggestion();
        s.setId(SUGGESTION_ID);
        s.setAgencyId(AGENCY_ID);
        s.setClientId(CLIENT_ID);
        s.setScopeType("CAMPAIGN");
        s.setScopeId(UUID.randomUUID());
        s.setSuggestionType("BUDGET_ADJUST");
        s.setPayloadJson("{}");
        s.setRationale("Test rationale");
        s.setConfidence(new BigDecimal("0.850"));
        s.setRiskLevel("MEDIUM");
        s.setStatus(status);
        s.setCreatedBy("AI");
        s.setCreatedAt(OffsetDateTime.now());
        return s;
    }

    // ──────── listSuggestions ────────

    @Test
    @DisplayName("listSuggestions — returns all for client")
    void getSuggestions_byClientId() {
        when(suggestionRepository.findAllByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(List.of(
                        buildSuggestion("PENDING"), buildSuggestion("APPROVED"),
                        buildSuggestion("REJECTED"), buildSuggestion("APPLIED"),
                        buildSuggestion("PENDING")
                ));

        List<SuggestionResponse> result = suggestionService.listSuggestions(AGENCY_ID, CLIENT_ID, null);

        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("listSuggestions — filtered by status PENDING")
    void getSuggestions_filteredByStatus() {
        AiSuggestion s1 = buildSuggestion("PENDING");
        AiSuggestion s2 = buildSuggestion("PENDING");

        when(suggestionRepository.findAllByAgencyIdAndClientIdAndStatus(AGENCY_ID, CLIENT_ID, "PENDING"))
                .thenReturn(List.of(s1, s2));

        List<SuggestionResponse> result = suggestionService.listSuggestions(AGENCY_ID, CLIENT_ID, "PENDING");

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(r -> assertThat(r.status()).isEqualTo("PENDING"));
    }

    // ──────── approveSuggestion ────────

    @Test
    @DisplayName("approveSuggestion — success: PENDING → APPROVED, reviewedBy set, reviewedAt not null")
    void approveSuggestion_success() {
        AiSuggestion s = buildSuggestion("PENDING");
        when(suggestionRepository.findByIdAndAgencyId(SUGGESTION_ID, AGENCY_ID)).thenReturn(Optional.of(s));
        when(suggestionRepository.save(any(AiSuggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        SuggestionResponse result = suggestionService.approveSuggestion(AGENCY_ID, SUGGESTION_ID);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.reviewedBy()).isEqualTo(USER_ID);
        assertThat(result.reviewedAt()).isNotNull();
        verify(auditService).log(eq(AGENCY_ID), eq(CLIENT_ID), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("approveSuggestion — not PENDING: throws IllegalStateException")
    void approveSuggestion_notPending() {
        AiSuggestion s = buildSuggestion("REJECTED");
        when(suggestionRepository.findByIdAndAgencyId(SUGGESTION_ID, AGENCY_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> suggestionService.approveSuggestion(AGENCY_ID, SUGGESTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    // ──────── rejectSuggestion ────────

    @Test
    @DisplayName("rejectSuggestion — success: PENDING → REJECTED")
    void rejectSuggestion_success() {
        AiSuggestion s = buildSuggestion("PENDING");
        when(suggestionRepository.findByIdAndAgencyId(SUGGESTION_ID, AGENCY_ID)).thenReturn(Optional.of(s));
        when(suggestionRepository.save(any(AiSuggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        SuggestionResponse result = suggestionService.rejectSuggestion(AGENCY_ID, SUGGESTION_ID);

        assertThat(result.status()).isEqualTo("REJECTED");
    }

    // ──────── applySuggestion ────────

    @Test
    @DisplayName("applySuggestion — success: APPROVED → delegates to executor, reloads as APPLIED")
    void applySuggestion_success() {
        AiSuggestion approved = buildSuggestion("APPROVED");
        AiSuggestion applied  = buildSuggestion("APPLIED");

        // First call: status check; second call: reload after executor runs
        when(suggestionRepository.findByIdAndAgencyId(SUGGESTION_ID, AGENCY_ID))
                .thenReturn(Optional.of(approved))
                .thenReturn(Optional.of(applied));

        when(executorService.executeSuggestion(AGENCY_ID, SUGGESTION_ID))
                .thenReturn(java.util.Map.of("status", "APPLIED"));

        SuggestionResponse result = suggestionService.applySuggestion(AGENCY_ID, SUGGESTION_ID);

        assertThat(result.status()).isEqualTo("APPLIED");
        verify(executorService).executeSuggestion(AGENCY_ID, SUGGESTION_ID);
        verify(auditService).log(eq(AGENCY_ID), eq(CLIENT_ID), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("applySuggestion — not APPROVED: throws IllegalStateException")
    void applySuggestion_notApproved() {
        AiSuggestion s = buildSuggestion("PENDING");
        when(suggestionRepository.findByIdAndAgencyId(SUGGESTION_ID, AGENCY_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> suggestionService.applySuggestion(AGENCY_ID, SUGGESTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }

    // ──────── not found ────────

    @Test
    @DisplayName("approveSuggestion — not found: throws ResourceNotFoundException")
    void approveSuggestion_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(suggestionRepository.findByIdAndAgencyId(unknownId, AGENCY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> suggestionService.approveSuggestion(AGENCY_ID, unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
