package com.mindbridge.agent.controller;

import com.mindbridge.agent.config.LlmProviderProperties;
import com.mindbridge.agent.config.VllmProperties;
import com.mindbridge.agent.service.SpringAiChatService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class LlmStatusController {

    private final LlmProviderProperties llmProviderProperties;
    private final VllmProperties vllmProperties;
    private final ObjectProvider<SpringAiChatService> springAiChatServiceProvider;

    @Value("${spring.ai.openai.base-url:}")
    private String springAiBaseUrl;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String springAiModel;

    public LlmStatusController(LlmProviderProperties llmProviderProperties,
                               VllmProperties vllmProperties,
                               ObjectProvider<SpringAiChatService> springAiChatServiceProvider) {
        this.llmProviderProperties = llmProviderProperties;
        this.vllmProperties = vllmProperties;
        this.springAiChatServiceProvider = springAiChatServiceProvider;
    }

    @GetMapping("/llm")
    public Map<String, Object> llmStatus() {
        String configuredProvider = normalize(llmProviderProperties.getProvider());
        boolean springAiAvailable = springAiChatServiceProvider.getIfAvailable() != null;
        boolean useSpringAi = "spring-ai".equals(configuredProvider) && springAiAvailable;

        String activeProvider = useSpringAi ? "spring-ai" : "vllm-compatible";
        String baseUrl = useSpringAi ? safe(springAiBaseUrl) : safe(vllmProperties.getBaseUrl());
        String model = useSpringAi ? safe(springAiModel) : safe(vllmProperties.getModel());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configuredProvider", configuredProvider);
        result.put("activeProvider", activeProvider);
        result.put("springAiAvailable", springAiAvailable);
        result.put("useRequestedModel", llmProviderProperties.isUseRequestedModel());
        result.put("model", model);
        result.put("baseUrl", baseUrl);
        return result;
    }

    private String normalize(String value) {
        if (value == null) {
            return "vllm";
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isEmpty() ? "vllm" : normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
