package com.mindagent.agent.controller;

import com.mindagent.agent.config.MindAgentAiProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class LlmStatusController {

    private final MindAgentAiProperties aiProperties;

    public LlmStatusController(MindAgentAiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @GetMapping("/llm")
    public Map<String, Object> llmStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chatProvider", normalize(aiProperties.getChat().getProvider()));
        result.put("agentProvider", normalize(aiProperties.getAgent().getProvider()));
        result.put("embeddingProvider", normalize(aiProperties.getEmbedding().getProvider()));
        result.put("rerankProvider", normalize(aiProperties.getRerank().getProvider()));
        result.put("toolCallingEnabled", aiProperties.getAgent().isToolCallingEnabled());
        result.put("chatModel", safe(aiProperties.getChat().getModel()));
        result.put("agentModel", safe(aiProperties.getAgent().getModel()));
        result.put("embeddingModel", safe(aiProperties.getEmbedding().getModel()));
        result.put("rerankModel", safe(aiProperties.getRerank().getModel()));
        result.put("useRequestedModel", aiProperties.isUseRequestedModel());
        return result;
    }

    private String normalize(String value) {
        if (value == null) {
            return "dashscope";
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isEmpty() ? "dashscope" : normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
