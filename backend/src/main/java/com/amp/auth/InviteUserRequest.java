package com.amp.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request DTO for inviting (creating) a new user within an agency.
 * <p>
 * Password is no longer required at invite time — the user sets their
 * own password when they accept the invitation via email.
 */
public record InviteUserRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,

        @NotBlank(message = "role is required")
        String role,

        UUID clientId
) {}
