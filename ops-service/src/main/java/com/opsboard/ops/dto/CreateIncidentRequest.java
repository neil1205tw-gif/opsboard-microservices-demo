package com.opsboard.ops.dto;

/**
 * Request body sent to incident-service's POST /incidents endpoint.
 * Field names mirror incident-service's CreateIncidentRequest.
 */
public class CreateIncidentRequest {

    private String title;
    private String description;
    private String serviceName;
    private String severity;

    public CreateIncidentRequest() {
    }

    public CreateIncidentRequest(String title, String description, String serviceName, String severity) {
        this.title = title;
        this.description = description;
        this.serviceName = serviceName;
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }
}
