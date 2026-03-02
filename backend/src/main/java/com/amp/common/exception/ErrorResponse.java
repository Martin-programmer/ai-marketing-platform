package com.amp.common.exception;

import java.util.Map;

/**
 * Standard error response body returned by all API error handlers.
 */
public record ErrorResponse(
        String code,
        String message,
        Map<String, Object> details,
        String correlationId
) {
}
