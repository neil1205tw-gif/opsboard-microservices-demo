package com.opsboard.ops.dto;

public class TriggerAlertErrorResponse {

    private String error;

    public TriggerAlertErrorResponse() {
    }

    public TriggerAlertErrorResponse(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
