package com.opsboard.incident.dto;

import com.opsboard.incident.entity.Runbook;

public class RunbookResponse {

    private Long id;
    private String serviceName;
    private String title;
    private String content;

    public static RunbookResponse from(Runbook runbook) {
        RunbookResponse response = new RunbookResponse();
        response.setId(runbook.getId());
        response.setServiceName(runbook.getServiceName());
        response.setTitle(runbook.getTitle());
        response.setContent(runbook.getContent());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
