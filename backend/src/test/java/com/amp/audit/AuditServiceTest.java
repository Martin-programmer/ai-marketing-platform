package com.amp.audit;

import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final UUID AGENCY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Mock
    private AuditLogRepository auditLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuditService auditService;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.set(new TenantContext(AGENCY_ID, USER_ID, "test@local", "AGENCY_ADMIN"));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("log — success: repository.save() is called, correlationId not null")
    void log_success() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(AGENCY_ID, CLIENT_ID, USER_ID, "AGENCY_ADMIN",
                AuditAction.CLIENT_CREATE, "CLIENT", CLIENT_ID,
                null, "after-state", UUID.randomUUID().toString());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getCorrelationId()).isNotNull();
        assertThat(saved.getAction()).isEqualTo("CLIENT_CREATE");
        assertThat(saved.getAgencyId()).isEqualTo(AGENCY_ID);
    }

    @Test
    @DisplayName("log — with explicit correlationId: preserved in saved entity")
    void log_withCorrelationId() {
        String corrId = "my-custom-correlation-id";
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(AGENCY_ID, CLIENT_ID, USER_ID, "AGENCY_ADMIN",
                AuditAction.CLIENT_UPDATE, "CLIENT", CLIENT_ID,
                "before", "after", corrId);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getCorrelationId()).isEqualTo(corrId);
    }

    @Test
    @DisplayName("log — null correlationId: auto-generated UUID is stored (not null)")
    void log_withNullCorrelationId() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(AGENCY_ID, CLIENT_ID, USER_ID, "AGENCY_ADMIN",
                AuditAction.CLIENT_PAUSE, "CLIENT", CLIENT_ID,
                "ACTIVE", "PAUSED", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        String correlationId = captor.getValue().getCorrelationId();
        assertThat(correlationId).isNotNull().isNotBlank();
        // Should be a valid UUID
        UUID.fromString(correlationId); // throws if not valid
    }

    @Test
    @DisplayName("log — null actorUserId: falls back to TenantContext")
    void log_fallbackToTenantContext() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.log(AGENCY_ID, CLIENT_ID, null, null,
                AuditAction.CLIENT_CREATE, "CLIENT", CLIENT_ID,
                null, "after", "corr-123");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(USER_ID);
        assertThat(saved.getActorRole()).isEqualTo("AGENCY_ADMIN");
    }
}
