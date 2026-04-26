package com.mindagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mindagent.rag")
public class RagProperties {

    private boolean enabled = true;
    /**
     * Supported values:
     * - local
     * - chroma
     */
    private String provider = "local";
    private int topK = 3;
    private int minChunkLength = 40;
    private boolean fallbackToLocal = true;
    private String knowledgeDir = "./storage/knowledge";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMinChunkLength() {
        return minChunkLength;
    }

    public void setMinChunkLength(int minChunkLength) {
        this.minChunkLength = minChunkLength;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isFallbackToLocal() {
        return fallbackToLocal;
    }

    public void setFallbackToLocal(boolean fallbackToLocal) {
        this.fallbackToLocal = fallbackToLocal;
    }

    public String getKnowledgeDir() {
        return knowledgeDir;
    }

    public void setKnowledgeDir(String knowledgeDir) {
        this.knowledgeDir = knowledgeDir;
    }
}
