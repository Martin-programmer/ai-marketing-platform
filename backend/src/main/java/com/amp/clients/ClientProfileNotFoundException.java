package com.amp.clients;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * Thrown when a client profile is not found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ClientProfileNotFoundException extends RuntimeException {

    public ClientProfileNotFoundException(UUID clientId) {
        super("Profile not found for client: " + clientId);
    }
}
