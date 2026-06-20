package com.opsboard.incident.service;

import com.opsboard.incident.dto.CreateIncidentRequest;
import com.opsboard.incident.dto.IncidentResponse;
import com.opsboard.incident.entity.Incident;
import com.opsboard.incident.entity.IncidentStatus;
import com.opsboard.incident.exception.IncidentNotFoundException;
import com.opsboard.incident.exception.InvalidStatusTransitionException;
import com.opsboard.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class IncidentService {

    private static final Map<IncidentStatus, IncidentStatus> NEXT_STATUS = new EnumMap<>(IncidentStatus.class);

    static {
        NEXT_STATUS.put(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING);
        NEXT_STATUS.put(IncidentStatus.INVESTIGATING, IncidentStatus.MITIGATED);
        NEXT_STATUS.put(IncidentStatus.MITIGATED, IncidentStatus.RESOLVED);
    }

    private final IncidentRepository incidentRepository;

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        Incident incident = new Incident();
        incident.setTitle(request.getTitle());
        incident.setDescription(request.getDescription());
        incident.setServiceName(request.getServiceName());
        incident.setSeverity(request.getSeverity());
        incident.setStatus(IncidentStatus.OPEN);
        Incident saved = incidentRepository.save(incident);
        return IncidentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> listIncidents() {
        return incidentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long id) {
        Incident incident = findIncidentOrThrow(id);
        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse updateStatus(Long id, IncidentStatus requestedStatus) {
        Incident incident = findIncidentOrThrow(id);
        IncidentStatus currentStatus = incident.getStatus();
        IncidentStatus expectedNextStatus = NEXT_STATUS.get(currentStatus);
        if (expectedNextStatus == null || expectedNextStatus != requestedStatus) {
            throw new InvalidStatusTransitionException(currentStatus, requestedStatus);
        }
        incident.setStatus(requestedStatus);
        Incident saved = incidentRepository.saveAndFlush(incident);
        return IncidentResponse.from(saved);
    }

    private Incident findIncidentOrThrow(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }
}
