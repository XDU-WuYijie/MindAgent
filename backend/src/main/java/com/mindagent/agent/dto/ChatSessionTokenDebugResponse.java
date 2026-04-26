package com.mindagent.agent.dto;

public class ChatSessionTokenDebugResponse {

    private Long sessionId;
    private String draftQuery;
    private Integer draftQueryTokens;
    private Integer estimatedPromptTokens;
    private Integer inputBudget;
    private Integer recentBudget;
    private Integer recentKeepTurns;
    private Integer compressTriggerTokens;
    private Integer summaryTargetTokens;
    private Integer summaryHardLimitTokens;
    private Integer ragBudget;
    private Integer minRagBudget;
    private SessionSummary summary;
    private RecentWindow recentWindow;
    private MessageStats totalMessages;
    private MessageStats compressedMessages;
    private MessageStats uncompressedMessages;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getDraftQuery() {
        return draftQuery;
    }

    public void setDraftQuery(String draftQuery) {
        this.draftQuery = draftQuery;
    }

    public Integer getDraftQueryTokens() {
        return draftQueryTokens;
    }

    public void setDraftQueryTokens(Integer draftQueryTokens) {
        this.draftQueryTokens = draftQueryTokens;
    }

    public Integer getEstimatedPromptTokens() {
        return estimatedPromptTokens;
    }

    public void setEstimatedPromptTokens(Integer estimatedPromptTokens) {
        this.estimatedPromptTokens = estimatedPromptTokens;
    }

    public Integer getInputBudget() {
        return inputBudget;
    }

    public void setInputBudget(Integer inputBudget) {
        this.inputBudget = inputBudget;
    }

    public Integer getRecentBudget() {
        return recentBudget;
    }

    public void setRecentBudget(Integer recentBudget) {
        this.recentBudget = recentBudget;
    }

    public Integer getRecentKeepTurns() {
        return recentKeepTurns;
    }

    public void setRecentKeepTurns(Integer recentKeepTurns) {
        this.recentKeepTurns = recentKeepTurns;
    }

    public Integer getCompressTriggerTokens() {
        return compressTriggerTokens;
    }

    public void setCompressTriggerTokens(Integer compressTriggerTokens) {
        this.compressTriggerTokens = compressTriggerTokens;
    }

    public Integer getSummaryTargetTokens() {
        return summaryTargetTokens;
    }

    public void setSummaryTargetTokens(Integer summaryTargetTokens) {
        this.summaryTargetTokens = summaryTargetTokens;
    }

    public Integer getSummaryHardLimitTokens() {
        return summaryHardLimitTokens;
    }

    public void setSummaryHardLimitTokens(Integer summaryHardLimitTokens) {
        this.summaryHardLimitTokens = summaryHardLimitTokens;
    }

    public Integer getRagBudget() {
        return ragBudget;
    }

    public void setRagBudget(Integer ragBudget) {
        this.ragBudget = ragBudget;
    }

    public Integer getMinRagBudget() {
        return minRagBudget;
    }

    public void setMinRagBudget(Integer minRagBudget) {
        this.minRagBudget = minRagBudget;
    }

    public SessionSummary getSummary() {
        return summary;
    }

    public void setSummary(SessionSummary summary) {
        this.summary = summary;
    }

    public RecentWindow getRecentWindow() {
        return recentWindow;
    }

    public void setRecentWindow(RecentWindow recentWindow) {
        this.recentWindow = recentWindow;
    }

    public MessageStats getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(MessageStats totalMessages) {
        this.totalMessages = totalMessages;
    }

    public MessageStats getCompressedMessages() {
        return compressedMessages;
    }

    public void setCompressedMessages(MessageStats compressedMessages) {
        this.compressedMessages = compressedMessages;
    }

    public MessageStats getUncompressedMessages() {
        return uncompressedMessages;
    }

    public void setUncompressedMessages(MessageStats uncompressedMessages) {
        this.uncompressedMessages = uncompressedMessages;
    }

    public static class SessionSummary {

        private Boolean present;
        private Integer tokenCount;
        private Integer version;
        private Long summarizedUntilMessageId;
        private String preview;

        public Boolean getPresent() {
            return present;
        }

        public void setPresent(Boolean present) {
            this.present = present;
        }

        public Integer getTokenCount() {
            return tokenCount;
        }

        public void setTokenCount(Integer tokenCount) {
            this.tokenCount = tokenCount;
        }

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
        }

        public Long getSummarizedUntilMessageId() {
            return summarizedUntilMessageId;
        }

        public void setSummarizedUntilMessageId(Long summarizedUntilMessageId) {
            this.summarizedUntilMessageId = summarizedUntilMessageId;
        }

        public String getPreview() {
            return preview;
        }

        public void setPreview(String preview) {
            this.preview = preview;
        }
    }

    public static class RecentWindow {

        private String source;
        private Integer messageCount;
        private Integer tokenCount;
        private Long firstMessageId;
        private Long lastMessageId;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public Integer getMessageCount() {
            return messageCount;
        }

        public void setMessageCount(Integer messageCount) {
            this.messageCount = messageCount;
        }

        public Integer getTokenCount() {
            return tokenCount;
        }

        public void setTokenCount(Integer tokenCount) {
            this.tokenCount = tokenCount;
        }

        public Long getFirstMessageId() {
            return firstMessageId;
        }

        public void setFirstMessageId(Long firstMessageId) {
            this.firstMessageId = firstMessageId;
        }

        public Long getLastMessageId() {
            return lastMessageId;
        }

        public void setLastMessageId(Long lastMessageId) {
            this.lastMessageId = lastMessageId;
        }
    }

    public static class MessageStats {

        private Integer messageCount;
        private Integer tokenCount;

        public Integer getMessageCount() {
            return messageCount;
        }

        public void setMessageCount(Integer messageCount) {
            this.messageCount = messageCount;
        }

        public Integer getTokenCount() {
            return tokenCount;
        }

        public void setTokenCount(Integer tokenCount) {
            this.tokenCount = tokenCount;
        }
    }
}
