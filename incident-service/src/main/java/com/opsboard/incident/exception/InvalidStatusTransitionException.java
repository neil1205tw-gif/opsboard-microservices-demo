package com.opsboard.incident.exception;

import com.opsboard.incident.entity.IncidentStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(IncidentStatus currentStatus, IncidentStatus requestedStatus) {
        super("Cannot transition incident from status " + currentStatus + " to " + requestedStatus);
    }
}
