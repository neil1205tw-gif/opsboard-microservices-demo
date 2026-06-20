package com.opsboard.ops.dto;

import java.time.Instant;

public class ServiceMetricsResponse {

    private String serviceName;
    private int latencyMs;
    private double errorRatePercent;
    private double cpuPercent;
    private Instant timestamp;

    public ServiceMetricsResponse() {
    }

    public ServiceMetricsResponse(String serviceName, int latencyMs, double errorRatePercent,
                                   double cpuPercent, Instant timestamp) {
        this.serviceName = serviceName;
        this.latencyMs = latencyMs;
        this.errorRatePercent = errorRatePercent;
        this.cpuPercent = cpuPercent;
        this.timestamp = timestamp;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(int latencyMs) {
        this.latencyMs = latencyMs;
    }

    public double getErrorRatePercent() {
        return errorRatePercent;
    }

    public void setErrorRatePercent(double errorRatePercent) {
        this.errorRatePercent = errorRatePercent;
    }

    public double getCpuPercent() {
        return cpuPercent;
    }

    public void setCpuPercent(double cpuPercent) {
        this.cpuPercent = cpuPercent;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
