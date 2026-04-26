package com.mindagent.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_memory_compress_log")
public class ChatMemoryCompressLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "from_message_id")
    private Long fromMessageId;

    @Column(name = "to_message_id")
    private Long toMessageId;

    @Column(name = "source_message_count", nullable = false)
    private Integer sourceMessageCount;

    @Column(name = "source_token_count", nullable = false)
    private Integer sourceTokenCount;

    @Column(name = "summary_version", nullable = false)
    private Integer summaryVersion;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

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

    public Long getFromMessageId() {
        return fromMessageId;
    }

    public void setFromMessageId(Long fromMessageId) {
        this.fromMessageId = fromMessageId;
    }

    public Long getToMessageId() {
        return toMessageId;
    }

    public void setToMessageId(Long toMessageId) {
        this.toMessageId = toMessageId;
    }

    public Integer getSourceMessageCount() {
        return sourceMessageCount;
    }

    public void setSourceMessageCount(Integer sourceMessageCount) {
        this.sourceMessageCount = sourceMessageCount;
    }

    public Integer getSourceTokenCount() {
        return sourceTokenCount;
    }

    public void setSourceTokenCount(Integer sourceTokenCount) {
        this.sourceTokenCount = sourceTokenCount;
    }

    public Integer getSummaryVersion() {
        return summaryVersion;
    }

    public void setSummaryVersion(Integer summaryVersion) {
        this.summaryVersion = summaryVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
