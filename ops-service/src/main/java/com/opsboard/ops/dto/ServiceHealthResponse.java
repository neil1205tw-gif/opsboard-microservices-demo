package com.opsboard.ops.dto;

import com.opsboard.ops.service.ServiceStatus;

import java.time.Instant;

public class ServiceHealthResponse {

    private String serviceName;
    private ServiceStatus status;
    private Instant lastCheckedAt;

    public ServiceHealthResponse() {
    }

    public ServiceHealthResponse(String serviceName, ServiceStatus status, Instant lastCheckedAt) {
        this.serviceName = serviceName;
        this.status = status;
        this.lastCheckedAt = lastCheckedAt;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }
}
