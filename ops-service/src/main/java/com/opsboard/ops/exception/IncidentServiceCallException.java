package com.opsboard.ops.exception;

/**
 * Thrown when calling incident-service to create an incident fails
 * (connection failure, timeout, or non-2xx response).
 */
public class IncidentServiceCallException extends RuntimeException {

    public IncidentServiceCallException(String message) {
        super(message);
    }

    public IncidentServiceCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
