package com.amp.clients;

/**
 * Request DTO for updating an existing client.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateClientRequest(
        String name,
        String industry,
        String timezone,
        String currency
) {
}
