package com.opsboard.ops.dto;

public class CooldownActiveResponse {

    private final String error = "cooldown_active";
    private long retryAfterSeconds;

    public CooldownActiveResponse(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getError() {
        return error;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
