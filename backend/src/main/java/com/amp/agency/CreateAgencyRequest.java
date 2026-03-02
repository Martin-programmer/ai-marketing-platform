package com.amp.agency;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new agency.
 */
public record CreateAgencyRequest(
        @NotBlank(message = "name is required")
        String name,
        String planCode
) {
    public CreateAgencyRequest {
        if (planCode == null || planCode.isBlank()) {
            planCode = "FREE";
        }
    }
}
