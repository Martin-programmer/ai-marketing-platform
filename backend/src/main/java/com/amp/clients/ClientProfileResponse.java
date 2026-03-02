package com.amp.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO representing a client profile.
 */
public record ClientProfileResponse(
        UUID id,
        UUID agencyId,
        UUID clientId,
        String website,
        Map<String, Object> profileJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ClientProfileResponse from(ClientProfile entity) {
        Map<String, Object> parsed;
        try {
            parsed = MAPPER.readValue(entity.getProfileJson(),
                    new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            parsed = Map.of("_raw", entity.getProfileJson());
        }
        return new ClientProfileResponse(
                entity.getId(),
                entity.getAgencyId(),
                entity.getClientId(),
                entity.getWebsite(),
                parsed,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
