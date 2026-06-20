package com.opsboard.ops.service;

import com.opsboard.ops.dto.ServiceMetricsResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates randomized but range-bound metrics snapshots for a monitored service,
 * based on its fixed baseline status (HEALTHY vs DEGRADED).
 */
@Component
public class MetricsGenerator {

    public ServiceMetricsResponse generate(MonitoredService service) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int latencyMs;
        double errorRatePercent;
        double cpuPercent;

        if (service.getBaselineStatus() == ServiceStatus.DEGRADED) {
            latencyMs = random.nextInt(500, 1501);
            errorRatePercent = random.nextDouble(15.0, 35.0);
            cpuPercent = random.nextDouble(70.0, 95.0);
        } else {
            latencyMs = random.nextInt(20, 81);
            errorRatePercent = random.nextDouble(0.0, 2.0);
            cpuPercent = random.nextDouble(10.0, 40.0);
        }

        return new ServiceMetricsResponse(
                service.getServiceName(),
                latencyMs,
                errorRatePercent,
                cpuPercent,
                Instant.now()
        );
    }
}
