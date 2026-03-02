package com.amp.clients;

import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    private static final UUID AGENCY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ClientService clientService;

    // ──────── helpers ────────

    private Client buildClient(String name, String status) {
        Client c = new Client();
        c.setId(CLIENT_ID);
        c.setAgencyId(AGENCY_ID);
        c.setName(name);
        c.setIndustry("ECOM");
        c.setStatus(status);
        c.setTimezone("Europe/Sofia");
        c.setCurrency("BGN");
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        return c;
    }

    // ──────── createClient ────────

    @Test
    @DisplayName("createClient — success: saves entity, logs audit, returns ACTIVE client")
    void createClient_success() {
        CreateClientRequest request = new CreateClientRequest("Test Client", "ECOM", "Europe/Sofia", "BGN");

        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(CLIENT_ID);
            return c;
        });

        Client result = clientService.createClient(AGENCY_ID, request);

        assertThat(result.getName()).isEqualTo("Test Client");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getAgencyId()).isEqualTo(AGENCY_ID);

        verify(clientRepository, times(1)).save(any(Client.class));
        verify(auditService, times(1)).log(eq(AGENCY_ID), eq(CLIENT_ID), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("createClient — duplicate name: DataIntegrityViolationException propagates")
    void createClient_duplicateName() {
        CreateClientRequest request = new CreateClientRequest("Dup Client", "ECOM", "Europe/Sofia", "BGN");

        when(clientRepository.save(any(Client.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> clientService.createClient(AGENCY_ID, request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ──────── listClients ────────

    @Test
    @DisplayName("listClients — returns all clients for agency")
    void getClients_returnsAll() {
        Client c1 = buildClient("Client 1", "ACTIVE");
        Client c2 = buildClient("Client 2", "ACTIVE");
        c2.setId(UUID.randomUUID());

        when(clientRepository.findAllByAgencyId(AGENCY_ID)).thenReturn(List.of(c1, c2));

        List<Client> result = clientService.listClients(AGENCY_ID);

        assertThat(result).hasSize(2);
    }

    // ──────── pauseClient ────────

    @Test
    @DisplayName("pauseClient — success: ACTIVE → PAUSED")
    void pauseClient_success() {
        Client client = buildClient("Test", "ACTIVE");
        when(clientRepository.findByIdAndAgencyId(CLIENT_ID, AGENCY_ID)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client result = clientService.pauseClient(AGENCY_ID, CLIENT_ID);

        assertThat(result.getStatus()).isEqualTo("PAUSED");
        verify(clientRepository).save(any(Client.class));
        verify(auditService).log(eq(AGENCY_ID), eq(CLIENT_ID), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("pauseClient — already PAUSED: still sets PAUSED (no guard in service)")
    void pauseClient_alreadyPaused() {
        // The current service does NOT guard against pausing an already-paused client;
        // it just sets PAUSED again. If a guard is added later, change this test.
        Client client = buildClient("Test", "PAUSED");
        when(clientRepository.findByIdAndAgencyId(CLIENT_ID, AGENCY_ID)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client result = clientService.pauseClient(AGENCY_ID, CLIENT_ID);

        assertThat(result.getStatus()).isEqualTo("PAUSED");
    }

    // ──────── activateClient ────────

    @Test
    @DisplayName("activateClient — success: PAUSED → ACTIVE")
    void activateClient_success() {
        Client client = buildClient("Test", "PAUSED");
        when(clientRepository.findByIdAndAgencyId(CLIENT_ID, AGENCY_ID)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client result = clientService.activateClient(AGENCY_ID, CLIENT_ID);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    // ──────── getClient ────────

    @Test
    @DisplayName("getClient — not found: throws ResourceNotFoundException")
    void getClient_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(clientRepository.findByIdAndAgencyId(unknownId, AGENCY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClient(AGENCY_ID, unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getClient — found: returns entity")
    void getClient_found() {
        Client client = buildClient("Found Client", "ACTIVE");
        when(clientRepository.findByIdAndAgencyId(CLIENT_ID, AGENCY_ID)).thenReturn(Optional.of(client));

        Client result = clientService.getClient(AGENCY_ID, CLIENT_ID);

        assertThat(result.getName()).isEqualTo("Found Client");
    }
}
