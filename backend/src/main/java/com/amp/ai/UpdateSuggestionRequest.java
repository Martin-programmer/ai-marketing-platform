package com.amp.ai;

import java.math.BigDecimal;

/**
 * Request DTO for updating a pending suggestion before approval.
 */
public record UpdateSuggestionRequest(
        String payloadJson,
        BigDecimal confidence
) {
}
