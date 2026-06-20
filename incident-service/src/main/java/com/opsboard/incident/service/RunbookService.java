package com.opsboard.incident.service;

import com.opsboard.incident.dto.RunbookResponse;
import com.opsboard.incident.repository.RunbookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RunbookService {

    private final RunbookRepository runbookRepository;

    public RunbookService(RunbookRepository runbookRepository) {
        this.runbookRepository = runbookRepository;
    }

    @Transactional(readOnly = true)
    public List<RunbookResponse> findByServiceName(String serviceName) {
        return runbookRepository.findAllByServiceName(serviceName).stream()
                .map(RunbookResponse::from)
                .toList();
    }
}
