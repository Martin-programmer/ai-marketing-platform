package com.amp.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class MetaPendingOAuthConnectionService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "meta:oauth:pending:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public MetaPendingOAuthConnectionService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void store(UUID agencyId, UUID clientId, String accessToken, OffsetDateTime tokenExpiresAt) {
        PendingMetaOAuthConnection payload = new PendingMetaOAuthConnection(agencyId, clientId, accessToken, tokenExpiresAt);
        try {
            redisTemplate.opsForValue().set(key(clientId), objectMapper.writeValueAsString(payload), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to store pending Meta OAuth token", e);
        }
    }

    public PendingMetaOAuthConnection get(UUID clientId) {
        String raw = redisTemplate.opsForValue().get(key(clientId));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, PendingMetaOAuthConnection.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read pending Meta OAuth token", e);
        }
    }

    public PendingMetaOAuthConnection getRequired(UUID clientId) {
        PendingMetaOAuthConnection pending = get(clientId);
        if (pending == null) {
            throw new IllegalStateException("Meta authorization expired. Please reconnect and select an ad account again.");
        }
        return pending;
    }

    public void clear(UUID clientId) {
        redisTemplate.delete(key(clientId));
    }

    private String key(UUID clientId) {
        return KEY_PREFIX + clientId;
    }

    public record PendingMetaOAuthConnection(
            UUID agencyId,
            UUID clientId,
            String accessToken,
            OffsetDateTime tokenExpiresAt
    ) {
    }
}
