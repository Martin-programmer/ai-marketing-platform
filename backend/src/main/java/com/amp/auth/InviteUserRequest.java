package com.amp.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for inviting (creating) a new user within an agency.
 */
public record InviteUserRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 6, message = "password must be at least 6 characters")
        String password,

        @NotBlank(message = "displayName is required")
        String displayName,

        @NotBlank(message = "role is required")
        String role,

        UUID clientId
) {}
