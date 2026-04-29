package com.mindagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mindagent.ai")
public class MindAgentAiProperties {

    private final ChatCapability chat = new ChatCapability("dashscope", "qwen-plus");
    private final ChatCapability agent = new ChatCapability("dashscope", "qwen-plus");
    private final ModelCapability embedding = new ModelCapability("dashscope", "text-embedding-v4");
    private final ModelCapability rerank = new ModelCapability("dashscope", "qwen3-rerank");
    private boolean useRequestedModel = false;

    public ChatCapability getChat() {
        return chat;
    }

    public ChatCapability getAgent() {
        return agent;
    }

    public ModelCapability getEmbedding() {
        return embedding;
    }

    public ModelCapability getRerank() {
        return rerank;
    }

    public boolean isUseRequestedModel() {
        return useRequestedModel;
    }

    public void setUseRequestedModel(boolean useRequestedModel) {
        this.useRequestedModel = useRequestedModel;
    }

    public static class ModelCapability {
        private String provider;
        private String model;

        public ModelCapability() {
        }

        public ModelCapability(String provider, String model) {
            this.provider = provider;
            this.model = model;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class ChatCapability extends ModelCapability {
        private boolean toolCallingEnabled = true;

        public ChatCapability() {
        }

        public ChatCapability(String provider, String model) {
            super(provider, model);
        }

        public boolean isToolCallingEnabled() {
            return toolCallingEnabled;
        }

        public void setToolCallingEnabled(boolean toolCallingEnabled) {
            this.toolCallingEnabled = toolCallingEnabled;
        }
    }
}
