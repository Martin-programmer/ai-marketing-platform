package com.amp.agency;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new agency together with its first admin user.
 */
public record CreateAgencyWithAdminRequest(
        @NotBlank(message = "name is required")
        String name,
        String planCode,

        @NotBlank(message = "adminEmail is required")
        @Email(message = "adminEmail must be a valid email")
        String adminEmail,

        @NotBlank(message = "adminPassword is required")
        @Size(min = 6, message = "adminPassword must be at least 6 characters")
        String adminPassword,

        String adminDisplayName
) {
    public CreateAgencyWithAdminRequest {
        if (planCode == null || planCode.isBlank()) {
            planCode = "FREE";
        }
        if (adminDisplayName == null || adminDisplayName.isBlank()) {
            adminDisplayName = "Agency Admin";
        }
    }
}
