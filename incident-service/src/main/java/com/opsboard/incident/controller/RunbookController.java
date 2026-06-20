package com.opsboard.incident.controller;

import com.opsboard.incident.dto.RunbookResponse;
import com.opsboard.incident.service.RunbookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/runbooks")
public class RunbookController {

    private final RunbookService runbookService;

    public RunbookController(RunbookService runbookService) {
        this.runbookService = runbookService;
    }

    @GetMapping
    public ResponseEntity<List<RunbookResponse>> getRunbooks(@RequestParam String serviceName) {
        return ResponseEntity.ok(runbookService.findByServiceName(serviceName));
    }
}
