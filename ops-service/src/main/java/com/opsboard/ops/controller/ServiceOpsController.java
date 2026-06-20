package com.opsboard.ops.controller;

import com.opsboard.ops.dto.CooldownActiveResponse;
import com.opsboard.ops.dto.ServiceHealthResponse;
import com.opsboard.ops.dto.ServiceMetricsResponse;
import com.opsboard.ops.service.AlertService;
import com.opsboard.ops.service.ServiceHealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/services")
public class ServiceOpsController {

    private final ServiceHealthService serviceHealthService;
    private final AlertService alertService;

    public ServiceOpsController(ServiceHealthService serviceHealthService, AlertService alertService) {
        this.serviceHealthService = serviceHealthService;
        this.alertService = alertService;
    }

    @GetMapping("/health")
    public ResponseEntity<List<ServiceHealthResponse>> getHealth() {
        return ResponseEntity.ok(serviceHealthService.getHealth());
    }

    @GetMapping("/{serviceName}/metrics")
    public ResponseEntity<ServiceMetricsResponse> getMetrics(@PathVariable String serviceName) {
        return ResponseEntity.ok(serviceHealthService.getMetrics(serviceName));
    }

    @PostMapping("/{serviceName}/trigger-alert")
    public ResponseEntity<Object> triggerAlert(@PathVariable String serviceName) {
        AlertService.AlertResult result = alertService.triggerAlert(serviceName);
        if (result.isCooldownActive()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new CooldownActiveResponse(result.getRetryAfterSeconds()));
        }
        Map<String, Object> incident = result.getIncident();
        return ResponseEntity.status(HttpStatus.CREATED).body(incident);
    }
}
