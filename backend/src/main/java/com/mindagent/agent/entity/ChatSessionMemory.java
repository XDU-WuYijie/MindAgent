package com.mindagent.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_session_memory")
public class ChatSessionMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "summary_text", nullable = false, columnDefinition = "LONGTEXT")
    private String summaryText = "";

    @Column(name = "summary_token_count", nullable = false)
    private Integer summaryTokenCount = 0;

    @Column(name = "summarized_until_message_id")
    private Long summarizedUntilMessageId;

    @Column(nullable = false)
    private Integer version = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public Integer getSummaryTokenCount() {
        return summaryTokenCount;
    }

    public void setSummaryTokenCount(Integer summaryTokenCount) {
        this.summaryTokenCount = summaryTokenCount;
    }

    public Long getSummarizedUntilMessageId() {
        return summarizedUntilMessageId;
    }

    public void setSummarizedUntilMessageId(Long summarizedUntilMessageId) {
        this.summarizedUntilMessageId = summarizedUntilMessageId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
