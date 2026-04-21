package com.mindbridge.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mindbridge.memory")
public class ChatMemoryProperties {

    private int historyLimit = 6;
    private boolean redisEnabled = true;
    private boolean fallbackToDb = true;
    private long ttlMinutes = 1440;
    private String keyPrefix = "mindbridge:chat:memory";
    private String defaultConversationId = "default";

    public int getHistoryLimit() {
        return historyLimit;
    }

    public void setHistoryLimit(int historyLimit) {
        this.historyLimit = historyLimit;
    }

    public boolean isRedisEnabled() {
        return redisEnabled;
    }

    public void setRedisEnabled(boolean redisEnabled) {
        this.redisEnabled = redisEnabled;
    }

    public boolean isFallbackToDb() {
        return fallbackToDb;
    }

    public void setFallbackToDb(boolean fallbackToDb) {
        this.fallbackToDb = fallbackToDb;
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getDefaultConversationId() {
        return defaultConversationId;
    }

    public void setDefaultConversationId(String defaultConversationId) {
        this.defaultConversationId = defaultConversationId;
    }
}
