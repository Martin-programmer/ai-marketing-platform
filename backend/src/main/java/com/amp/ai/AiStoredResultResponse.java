package com.amp.ai;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AiStoredResultResponse(
        UUID id,
        OffsetDateTime createdAt,
        String preview,
        Map<String, Object> data
) {}