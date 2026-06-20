package com.opsboard.ops.service;

import com.opsboard.ops.dto.CreateIncidentRequest;
import com.opsboard.ops.dto.ServiceMetricsResponse;
import com.opsboard.ops.exception.IncidentServiceCallException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AlertService {

    private static final Duration COOLDOWN_DURATION = Duration.ofSeconds(60);
    private static final String COOLDOWN_KEY_PREFIX = "alert:cooldown:";

    private final ServiceHealthService serviceHealthService;
    private final MetricsGenerator metricsGenerator;
    private final StringRedisTemplate redisTemplate;
    private final RestClient incidentServiceRestClient;

    public AlertService(ServiceHealthService serviceHealthService,
                         MetricsGenerator metricsGenerator,
                         StringRedisTemplate redisTemplate,
                         RestClient incidentServiceRestClient) {
        this.serviceHealthService = serviceHealthService;
        this.metricsGenerator = metricsGenerator;
        this.redisTemplate = redisTemplate;
        this.incidentServiceRestClient = incidentServiceRestClient;
    }

    public AlertResult triggerAlert(String serviceName) {
        MonitoredService service = serviceHealthService.resolveOrThrow(serviceName);
        String cooldownKey = COOLDOWN_KEY_PREFIX + service.getServiceName();

        Boolean cooldownSet = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", COOLDOWN_DURATION);

        if (!Boolean.TRUE.equals(cooldownSet)) {
            long retryAfterSeconds = getRetryAfterSeconds(cooldownKey);
            return AlertResult.cooldownActive(retryAfterSeconds);
        }

        ServiceMetricsResponse metrics = metricsGenerator.generate(service);
        Map<String, Object> incident = createIncident(service, metrics);
        return AlertResult.created(incident);
    }

    private long getRetryAfterSeconds(String cooldownKey) {
        Long ttl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            return COOLDOWN_DURATION.toSeconds();
        }
        return ttl;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createIncident(MonitoredService service, ServiceMetricsResponse metrics) {
        String title = service.getServiceName() + " health alert triggered";
        String description = String.format(Locale.ROOT,
                "Automated alert triggered for %s. Snapshot metrics: latencyMs=%d, errorRatePercent=%.2f, cpuPercent=%.2f",
                service.getServiceName(), metrics.getLatencyMs(), metrics.getErrorRatePercent(), metrics.getCpuPercent());

        CreateIncidentRequest request = new CreateIncidentRequest(
                title, description, service.getServiceName(), "HIGH");

        try {
            Map<String, Object> response = incidentServiceRestClient.post()
                    .uri("/incidents")
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new IncidentServiceCallException("incident-service returned an empty response body");
            }
            return response;
        } catch (RestClientException ex) {
            throw new IncidentServiceCallException("Failed to create incident via incident-service: " + ex.getMessage(), ex);
        }
    }

    public static final class AlertResult {
        private final boolean cooldownActive;
        private final long retryAfterSeconds;
        private final Map<String, Object> incident;

        private AlertResult(boolean cooldownActive, long retryAfterSeconds, Map<String, Object> incident) {
            this.cooldownActive = cooldownActive;
            this.retryAfterSeconds = retryAfterSeconds;
            this.incident = incident;
        }

        public static AlertResult cooldownActive(long retryAfterSeconds) {
            return new AlertResult(true, retryAfterSeconds, null);
        }

        public static AlertResult created(Map<String, Object> incident) {
            return new AlertResult(false, 0, incident);
        }

        public boolean isCooldownActive() {
            return cooldownActive;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }

        public Map<String, Object> getIncident() {
            return incident;
        }
    }
}
