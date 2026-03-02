package com.amp.clients;

import com.amp.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Business logic for client profile operations.
 */
@Service
public class ClientProfileService {

    private final ClientProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public ClientProfileService(ClientProfileRepository profileRepository,
                                ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ClientProfile getProfile(UUID clientId) {
        return profileRepository.findByClientId(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientProfile", clientId));
    }

    @Transactional
    public ClientProfile upsertProfile(UUID agencyId, UUID clientId,
                                       ClientProfileRequest request) {
        String json = toJson(request.profileJson());

        ClientProfile profile = profileRepository.findByClientId(clientId)
                .orElseGet(() -> {
                    ClientProfile p = new ClientProfile();
                    p.setAgencyId(agencyId);
                    p.setClientId(clientId);
                    OffsetDateTime now = OffsetDateTime.now();
                    p.setCreatedAt(now);
                    p.setUpdatedAt(now);
                    return p;
                });

        profile.setWebsite(request.website());
        profile.setProfileJson(json);

        return profileRepository.save(profile);
    }

    private String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid profile JSON", e);
        }
    }
}
