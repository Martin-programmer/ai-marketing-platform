package com.amp.meta;

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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetaServiceTest {

    private static final UUID AGENCY_ID     = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID     = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID USER_ID       = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID CONNECTION_ID = UUID.randomUUID();

    @Mock private MetaConnectionRepository connectionRepository;
    @Mock private MetaSyncJobRepository syncJobRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private MetaService metaService;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.set(new TenantContext(AGENCY_ID, USER_ID, "test@local", "AGENCY_ADMIN"));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // ──────── helpers ────────

    private MetaConnection buildConnection(String status) {
        MetaConnection conn = new MetaConnection();
        conn.setId(CONNECTION_ID);
        conn.setAgencyId(AGENCY_ID);
        conn.setClientId(CLIENT_ID);
        conn.setAdAccountId("act_1234567890");
        conn.setPixelId("px_9876543210");
        conn.setPageId("page_111222333");
        conn.setAccessTokenEnc(new byte[0]);
        conn.setTokenKeyId("test-key");
        conn.setStatus(status);
        conn.setConnectedAt(OffsetDateTime.now());
        conn.setCreatedAt(OffsetDateTime.now());
        conn.setUpdatedAt(OffsetDateTime.now());
        return conn;
    }

    private MetaSyncJob buildSyncJob(String jobStatus) {
        MetaSyncJob job = new MetaSyncJob();
        job.setId(UUID.randomUUID());
        job.setAgencyId(AGENCY_ID);
        job.setClientId(CLIENT_ID);
        job.setJobType("DAILY");
        job.setJobStatus(jobStatus);
        job.setIdempotencyKey("test-key-" + UUID.randomUUID());
        job.setRequestedAt(OffsetDateTime.now());
        return job;
    }

    // ──────── getConnection ────────

    @Test
    @DisplayName("getConnection — exists: returns response with adAccountId")
    void getConnection_exists() {
        MetaConnection conn = buildConnection("CONNECTED");
        when(connectionRepository.findByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(Optional.of(conn));

        MetaConnectionResponse result = metaService.getConnection(AGENCY_ID, CLIENT_ID);

        assertThat(result).isNotNull();
        assertThat(result.adAccountId()).isEqualTo("act_1234567890");
        assertThat(result.status()).isEqualTo("CONNECTED");
    }

    @Test
    @DisplayName("getConnection — not exists: returns null")
    void getConnection_notExists() {
        when(connectionRepository.findByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(Optional.empty());

        MetaConnectionResponse result = metaService.getConnection(AGENCY_ID, CLIENT_ID);

        assertThat(result).isNull();
    }

    // ──────── disconnect ────────

    @Test
    @DisplayName("disconnect — success: CONNECTED → DISCONNECTED, audit logged")
    void disconnect_success() {
        MetaConnection conn = buildConnection("CONNECTED");
        when(connectionRepository.findByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(Optional.of(conn));
        when(connectionRepository.save(any(MetaConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        metaService.disconnect(AGENCY_ID, CLIENT_ID);

        assertThat(conn.getStatus()).isEqualTo("DISCONNECTED");
        verify(connectionRepository).save(any(MetaConnection.class));
        verify(auditService).log(eq(AGENCY_ID), eq(CLIENT_ID), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("disconnect — already DISCONNECTED: still sets DISCONNECTED (no guard)")
    void disconnect_alreadyDisconnected() {
        // The current service does NOT guard against disconnecting an already-disconnected connection.
        MetaConnection conn = buildConnection("DISCONNECTED");
        when(connectionRepository.findByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(Optional.of(conn));
        when(connectionRepository.save(any(MetaConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        metaService.disconnect(AGENCY_ID, CLIENT_ID);

        assertThat(conn.getStatus()).isEqualTo("DISCONNECTED");
    }

    @Test
    @DisplayName("disconnect — no connection: throws ResourceNotFoundException")
    void disconnect_notFound() {
        when(connectionRepository.findByAgencyIdAndClientId(AGENCY_ID, CLIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> metaService.disconnect(AGENCY_ID, CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ──────── triggerSync ────────

    @Test
    @DisplayName("triggerSync — success: job created with status PENDING, idempotencyKey not null")
    void triggerSync_success() {
        when(syncJobRepository.save(any(MetaSyncJob.class))).thenAnswer(inv -> {
            MetaSyncJob job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        MetaSyncJobResponse result = metaService.triggerSync(AGENCY_ID, CLIENT_ID, "DAILY");

        assertThat(result.jobStatus()).isEqualTo("PENDING");
        assertThat(result.jobType()).isEqualTo("DAILY");
        assertThat(result.idempotencyKey()).isNotNull().isNotBlank();
        verify(syncJobRepository).save(any(MetaSyncJob.class));
    }

    // ──────── getSyncStatus ────────

    @Test
    @DisplayName("getSyncStatus — returns latest sync job")
    void getSyncStatus_returnsLatest() {
        MetaSyncJob job = buildSyncJob("SUCCESS");
        when(syncJobRepository.findFirstByClientIdOrderByRequestedAtDesc(CLIENT_ID))
                .thenReturn(Optional.of(job));

        MetaSyncJobResponse result = metaService.getSyncStatus(AGENCY_ID, CLIENT_ID);

        assertThat(result).isNotNull();
        assertThat(result.jobStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("getSyncStatus — no jobs: returns null")
    void getSyncStatus_noJobs() {
        when(syncJobRepository.findFirstByClientIdOrderByRequestedAtDesc(CLIENT_ID))
                .thenReturn(Optional.empty());

        MetaSyncJobResponse result = metaService.getSyncStatus(AGENCY_ID, CLIENT_ID);

        assertThat(result).isNull();
    }
}
