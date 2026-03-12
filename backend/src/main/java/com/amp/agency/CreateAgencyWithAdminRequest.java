package com.amp.agency;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new agency together with its first admin user.
 * <p>
 * The admin user receives an invitation email and sets their own password.
 */
public record CreateAgencyWithAdminRequest(
        @NotBlank(message = "name is required")
        String name,
        String planCode,

        @Email(message = "adminEmail must be a valid email")
        String adminEmail,

        String adminDisplayName
) {
    public CreateAgencyWithAdminRequest {
        if (planCode == null || planCode.isBlank()) {
            planCode = "FREE";
        }
        if (adminEmail != null && adminEmail.isBlank()) {
            adminEmail = null;
        }
        if (adminDisplayName == null || adminDisplayName.isBlank()) {
            adminDisplayName = "Agency Admin";
        }
    }
}
