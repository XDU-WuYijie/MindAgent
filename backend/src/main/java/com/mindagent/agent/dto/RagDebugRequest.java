package com.mindagent.agent.dto;

import jakarta.validation.constraints.NotBlank;

public class RagDebugRequest {

    @NotBlank
    private String query;

    private String model;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
