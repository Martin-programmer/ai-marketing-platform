package com.amp.agency;

/**
 * Request DTO for updating an existing agency.
 */
public record UpdateAgencyRequest(
        String name,
        String planCode
) {
}
