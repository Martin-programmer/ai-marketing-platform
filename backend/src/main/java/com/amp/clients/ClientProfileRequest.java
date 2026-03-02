package com.amp.clients;

import java.util.Map;

/**
 * Request DTO for creating or updating a client profile.
 */
public record ClientProfileRequest(
        String website,
        Map<String, Object> profileJson
) {
}
