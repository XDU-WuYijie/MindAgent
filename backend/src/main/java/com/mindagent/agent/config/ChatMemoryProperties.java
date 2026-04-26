package com.mindagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mindagent.memory")
public class ChatMemoryProperties {

    private int recentKeepTurns = 6;
    private boolean redisEnabled = true;
    private long recentTtlMinutes = 1440;
    private String recentKeyPrefix = "mindagent:chat:recent";
    private int inputBudget = 6144;
    private int compressTriggerTokens = 3000;
    private int summaryTargetTokens = 700;
    private int summaryHardLimitTokens = 1000;
    private int ragBudget = 2000;
    private int minRagBudget = 800;

    public int getRecentKeepTurns() {
        return recentKeepTurns;
    }

    public void setRecentKeepTurns(int recentKeepTurns) {
        this.recentKeepTurns = recentKeepTurns;
    }

    public boolean isRedisEnabled() {
        return redisEnabled;
    }

    public void setRedisEnabled(boolean redisEnabled) {
        this.redisEnabled = redisEnabled;
    }

    public long getRecentTtlMinutes() {
        return recentTtlMinutes;
    }

    public void setRecentTtlMinutes(long recentTtlMinutes) {
        this.recentTtlMinutes = recentTtlMinutes;
    }

    public String getRecentKeyPrefix() {
        return recentKeyPrefix;
    }

    public void setRecentKeyPrefix(String recentKeyPrefix) {
        this.recentKeyPrefix = recentKeyPrefix;
    }

    public int getInputBudget() {
        return inputBudget;
    }

    public void setInputBudget(int inputBudget) {
        this.inputBudget = inputBudget;
    }

    public int getCompressTriggerTokens() {
        return compressTriggerTokens;
    }

    public void setCompressTriggerTokens(int compressTriggerTokens) {
        this.compressTriggerTokens = compressTriggerTokens;
    }

    public int getSummaryTargetTokens() {
        return summaryTargetTokens;
    }

    public void setSummaryTargetTokens(int summaryTargetTokens) {
        this.summaryTargetTokens = summaryTargetTokens;
    }

    public int getSummaryHardLimitTokens() {
        return summaryHardLimitTokens;
    }

    public void setSummaryHardLimitTokens(int summaryHardLimitTokens) {
        this.summaryHardLimitTokens = summaryHardLimitTokens;
    }

    public int getRagBudget() {
        return ragBudget;
    }

    public void setRagBudget(int ragBudget) {
        this.ragBudget = ragBudget;
    }

    public int getMinRagBudget() {
        return minRagBudget;
    }

    public void setMinRagBudget(int minRagBudget) {
        this.minRagBudget = minRagBudget;
    }
}
