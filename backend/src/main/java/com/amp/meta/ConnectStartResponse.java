package com.amp.meta;

/**
 * Response DTO for the OAuth connect-start flow.
 */
public record ConnectStartResponse(
        String authorizationUrl,
        String state
) {
}
