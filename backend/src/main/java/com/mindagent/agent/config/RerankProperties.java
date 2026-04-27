package com.mindagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mindagent.rag.rerank")
public class RerankProperties {

    private boolean enabled = true;
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-api/v1";
    private String apiKey = "";
    private String path = "/reranks";
    private String model = "qwen3-rerank";
    private int candidateTopK = 30;
    private int topN = 5;
    private String instruct = "Given a user question, rank the retrieved passages by how well they directly answer the question.";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getCandidateTopK() {
        return candidateTopK;
    }

    public void setCandidateTopK(int candidateTopK) {
        this.candidateTopK = candidateTopK;
    }

    public int getTopN() {
        return topN;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }

    public String getInstruct() {
        return instruct;
    }

    public void setInstruct(String instruct) {
        this.instruct = instruct;
    }
}
