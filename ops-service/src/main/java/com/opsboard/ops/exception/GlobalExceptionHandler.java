package com.opsboard.ops.exception;

import com.opsboard.ops.dto.TriggerAlertErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnknownServiceException.class)
    public ResponseEntity<TriggerAlertErrorResponse> handleUnknownService(UnknownServiceException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new TriggerAlertErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(IncidentServiceCallException.class)
    public ResponseEntity<TriggerAlertErrorResponse> handleIncidentServiceCallFailure(
            IncidentServiceCallException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new TriggerAlertErrorResponse(ex.getMessage()));
    }
}
