package com.opsboard.incident.repository;

import com.opsboard.incident.entity.IncidentTimelineEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentTimelineEntryRepository extends JpaRepository<IncidentTimelineEntry, Long> {

    List<IncidentTimelineEntry> findAllByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
