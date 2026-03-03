package com.amp.clients;

import com.amp.audit.AuditAction;
import com.amp.audit.AuditService;
import com.amp.common.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for client CRUD operations.
 */
@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final AuditService auditService;

    public ClientService(ClientRepository clientRepository, AuditService auditService) {
        this.clientRepository = clientRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "clients", key = "#agencyId")
    public List<Client> listClients(UUID agencyId) {
        return clientRepository.findAllByAgencyId(agencyId);
    }

    @Transactional
    @CacheEvict(value = "clients", key = "#agencyId")
    public Client createClient(UUID agencyId, CreateClientRequest request) {
        Client client = new Client();
        client.setAgencyId(agencyId);
        client.setName(request.name());
        client.setIndustry(request.industry());
        client.setStatus("ACTIVE");
        client.setTimezone(request.timezone());
        client.setCurrency(request.currency());

        OffsetDateTime now = OffsetDateTime.now();
        client.setCreatedAt(now);
        client.setUpdatedAt(now);

        Client saved = clientRepository.save(client);

        auditService.log(agencyId, saved.getId(), null, "SYSTEM",
                AuditAction.CLIENT_CREATE, "CLIENT", saved.getId(),
                null, saved, UUID.randomUUID().toString());

        return saved;
    }

    @Transactional(readOnly = true)
    public Client getClient(UUID agencyId, UUID clientId) {
        return clientRepository.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
    }

    @Transactional
    @CacheEvict(value = "clients", key = "#agencyId")
    public Client updateClient(UUID agencyId, UUID clientId, UpdateClientRequest request) {
        Client client = clientRepository.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        Client before = snapshot(client);

        if (request.name() != null)     client.setName(request.name());
        if (request.industry() != null) client.setIndustry(request.industry());
        if (request.timezone() != null) client.setTimezone(request.timezone());
        if (request.currency() != null) client.setCurrency(request.currency());

        Client saved = clientRepository.save(client);

        auditService.log(agencyId, clientId, null, "SYSTEM",
                AuditAction.CLIENT_UPDATE, "CLIENT", clientId,
                before, saved, UUID.randomUUID().toString());

        return saved;
    }

    @Transactional
    @CacheEvict(value = "clients", key = "#agencyId")
    public Client pauseClient(UUID agencyId, UUID clientId) {
        Client client = clientRepository.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        String previousStatus = client.getStatus();
        client.setStatus("PAUSED");
        Client saved = clientRepository.save(client);

        auditService.log(agencyId, clientId, null, "SYSTEM",
                AuditAction.CLIENT_PAUSE, "CLIENT", clientId,
                previousStatus, "PAUSED", UUID.randomUUID().toString());

        return saved;
    }

    @Transactional
    @CacheEvict(value = "clients", key = "#agencyId")
    public Client activateClient(UUID agencyId, UUID clientId) {
        Client client = clientRepository.findByIdAndAgencyId(clientId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        String previousStatus = client.getStatus();
        client.setStatus("ACTIVE");
        Client saved = clientRepository.save(client);

        auditService.log(agencyId, clientId, null, "SYSTEM",
                AuditAction.CLIENT_ACTIVATE, "CLIENT", clientId,
                previousStatus, "ACTIVE", UUID.randomUUID().toString());

        return saved;
    }

    /**
     * Creates a detached snapshot of the client's current state for audit purposes.
     */
    private Client snapshot(Client source) {
        Client copy = new Client();
        copy.setId(source.getId());
        copy.setAgencyId(source.getAgencyId());
        copy.setName(source.getName());
        copy.setIndustry(source.getIndustry());
        copy.setStatus(source.getStatus());
        copy.setTimezone(source.getTimezone());
        copy.setCurrency(source.getCurrency());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
