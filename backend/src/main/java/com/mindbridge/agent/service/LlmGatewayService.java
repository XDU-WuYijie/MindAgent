package com.mindbridge.agent.service;

import com.mindbridge.agent.config.LlmProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Primary
public class LlmGatewayService implements ChatModelGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGatewayService.class);

    private final VllmChatService vllmChatService;
    private final ObjectProvider<SpringAiChatService> springAiChatServiceProvider;
    private final LlmProviderProperties llmProviderProperties;

    public LlmGatewayService(VllmChatService vllmChatService,
                             ObjectProvider<SpringAiChatService> springAiChatServiceProvider,
                             LlmProviderProperties llmProviderProperties) {
        this.vllmChatService = vllmChatService;
        this.springAiChatServiceProvider = springAiChatServiceProvider;
        this.llmProviderProperties = llmProviderProperties;
    }

    @Override
    public Flux<String> streamChat(List<Map<String, Object>> messages, String requestedModel) {
        return delegate().streamChat(messages, effectiveModel(requestedModel));
    }

    @Override
    public Mono<String> completeOnce(List<Map<String, Object>> messages, String requestedModel) {
        return delegate().completeOnce(messages, effectiveModel(requestedModel));
    }

    private ChatModelGateway delegate() {
        String provider = llmProviderProperties.getProvider() == null
                ? "vllm"
                : llmProviderProperties.getProvider().trim().toLowerCase(Locale.ROOT);

        if ("spring-ai".equals(provider)) {
            SpringAiChatService springAi = springAiChatServiceProvider.getIfAvailable();
            if (springAi != null) {
                return springAi;
            }
            log.warn("LLM provider 'spring-ai' is configured but SpringAiChatService is unavailable. Falling back to vLLM-compatible gateway.");
        }
        return vllmChatService;
    }

    private String effectiveModel(String requestedModel) {
        if (!llmProviderProperties.isUseRequestedModel()) {
            return null;
        }
        return requestedModel;
    }
}
