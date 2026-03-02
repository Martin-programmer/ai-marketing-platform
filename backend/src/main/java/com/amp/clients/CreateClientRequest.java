package com.amp.clients;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new client.
 */
public record CreateClientRequest(
        @NotBlank(message = "Client name is required")
        String name,
        String industry,
        String timezone,
        String currency
) {
    public CreateClientRequest {
        if (timezone == null || timezone.isBlank()) timezone = "Europe/Sofia";
        if (currency == null || currency.isBlank()) currency = "BGN";
    }
}
