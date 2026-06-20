package com.opsboard.ops.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Fixed set of services monitored by ops-service. Names must stay in sync with the
 * runbook service names already seeded in incident-service (payment-api, checkout-api,
 * notification-worker) — this is a cross-service naming contract, not configurable.
 */
public enum MonitoredService {

    PAYMENT_API("payment-api", ServiceStatus.HEALTHY),
    CHECKOUT_API("checkout-api", ServiceStatus.DEGRADED),
    NOTIFICATION_WORKER("notification-worker", ServiceStatus.HEALTHY);

    private final String serviceName;
    private final ServiceStatus baselineStatus;

    MonitoredService(String serviceName, ServiceStatus baselineStatus) {
        this.serviceName = serviceName;
        this.baselineStatus = baselineStatus;
    }

    public String getServiceName() {
        return serviceName;
    }

    public ServiceStatus getBaselineStatus() {
        return baselineStatus;
    }

    public static Optional<MonitoredService> fromServiceName(String serviceName) {
        return Arrays.stream(values())
                .filter(service -> service.serviceName.equals(serviceName))
                .findFirst();
    }
}
