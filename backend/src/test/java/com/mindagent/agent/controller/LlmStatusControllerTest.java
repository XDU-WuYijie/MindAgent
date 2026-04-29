package com.mindagent.agent.controller;

import com.mindagent.agent.config.MindAgentAiProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmStatusControllerTest {

    @Test
    void shouldExposeCapabilityBasedStatus() {
        MindAgentAiProperties properties = new MindAgentAiProperties();
        properties.getChat().setProvider("dashscope");
        properties.getChat().setModel("qwen-plus");
        properties.getAgent().setProvider("dashscope");
        properties.getAgent().setModel("qwen-max");
        properties.getEmbedding().setProvider("dashscope");
        properties.getEmbedding().setModel("text-embedding-v4");
        properties.getRerank().setProvider("dashscope");
        properties.getRerank().setModel("qwen3-rerank");

        LlmStatusController controller = new LlmStatusController(properties);
        Map<String, Object> result = controller.llmStatus();

        assertEquals("dashscope", result.get("chatProvider"));
        assertEquals("dashscope", result.get("agentProvider"));
        assertEquals("text-embedding-v4", result.get("embeddingModel"));
        assertEquals("qwen3-rerank", result.get("rerankModel"));
        assertEquals("qwen-max", result.get("agentModel"));
    }
}
