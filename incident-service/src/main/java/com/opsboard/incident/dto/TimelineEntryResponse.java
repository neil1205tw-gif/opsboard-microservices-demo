package com.opsboard.incident.dto;

import com.opsboard.incident.entity.IncidentStatus;
import com.opsboard.incident.entity.IncidentTimelineEntry;

import java.time.Instant;

public class TimelineEntryResponse {

    private Long id;
    private Long incidentId;
    private IncidentStatus fromStatus;
    private IncidentStatus toStatus;
    private String note;
    private Instant createdAt;

    public static TimelineEntryResponse from(IncidentTimelineEntry entry) {
        TimelineEntryResponse response = new TimelineEntryResponse();
        response.setId(entry.getId());
        response.setIncidentId(entry.getIncidentId());
        response.setFromStatus(entry.getFromStatus());
        response.setToStatus(entry.getToStatus());
        response.setNote(entry.getNote());
        response.setCreatedAt(entry.getCreatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public IncidentStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(IncidentStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public IncidentStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(IncidentStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
