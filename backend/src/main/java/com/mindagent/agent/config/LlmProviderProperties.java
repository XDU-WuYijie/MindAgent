package com.mindagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mindagent.llm")
public class LlmProviderProperties {

    /**
     * Supported values:
     * - vllm
     * - spring-ai
     */
    private String provider = "vllm";
    private boolean useRequestedModel = true;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isUseRequestedModel() {
        return useRequestedModel;
    }

    public void setUseRequestedModel(boolean useRequestedModel) {
        this.useRequestedModel = useRequestedModel;
    }
}
