package com.opsboard.ops.service;

import com.opsboard.ops.dto.ServiceHealthResponse;
import com.opsboard.ops.dto.ServiceMetricsResponse;
import com.opsboard.ops.exception.UnknownServiceException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class ServiceHealthService {

    private final MetricsGenerator metricsGenerator;

    public ServiceHealthService(MetricsGenerator metricsGenerator) {
        this.metricsGenerator = metricsGenerator;
    }

    public List<ServiceHealthResponse> getHealth() {
        Instant now = Instant.now();
        return Arrays.stream(MonitoredService.values())
                .map(service -> new ServiceHealthResponse(
                        service.getServiceName(),
                        service.getBaselineStatus(),
                        now))
                .toList();
    }

    public ServiceMetricsResponse getMetrics(String serviceName) {
        MonitoredService service = resolveOrThrow(serviceName);
        return metricsGenerator.generate(service);
    }

    public MonitoredService resolveOrThrow(String serviceName) {
        return MonitoredService.fromServiceName(serviceName)
                .orElseThrow(() -> new UnknownServiceException(serviceName));
    }
}
