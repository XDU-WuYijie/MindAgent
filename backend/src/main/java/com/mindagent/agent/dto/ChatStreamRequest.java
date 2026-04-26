package com.mindagent.agent.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatStreamRequest {

    @NotBlank
    private String query;

    private String model;
    private Long userId = 1L;
    private String conversationId;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
