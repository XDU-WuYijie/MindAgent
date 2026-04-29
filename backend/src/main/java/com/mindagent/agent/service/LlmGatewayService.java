package com.mindagent.agent.service;

import com.mindagent.agent.config.MindAgentAiProperties;
import org.springframework.ai.tool.ToolCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final DashScopeChatGateway dashScopeChatGateway;
    private final OllamaChatGateway ollamaChatGateway;
    private final MindAgentAiProperties aiProperties;

    public LlmGatewayService(DashScopeChatGateway dashScopeChatGateway,
                             OllamaChatGateway ollamaChatGateway,
                             MindAgentAiProperties aiProperties) {
        this.dashScopeChatGateway = dashScopeChatGateway;
        this.ollamaChatGateway = ollamaChatGateway;
        this.aiProperties = aiProperties;
    }

    @Override
    public Flux<String> streamChat(List<Map<String, Object>> messages, String requestedModel) {
        return chatDelegate().streamChat(messages, effectiveModel(requestedModel, aiProperties.getChat().getModel()));
    }

    @Override
    public Mono<String> completeOnce(List<Map<String, Object>> messages, String requestedModel) {
        return chatDelegate().completeOnce(messages, effectiveModel(requestedModel, aiProperties.getChat().getModel()));
    }

    @Override
    public Mono<ToolChatResult> completeWithTools(List<Map<String, Object>> messages,
                                                  List<ToolCallback> toolCallbacks,
                                                  String requestedModel) {
        ChatModelGateway delegate = agentDelegate();
        if (!delegate.supportsToolCalling()) {
            log.warn("Agent provider '{}' does not support tool calling. Falling back to DashScope.", aiProperties.getAgent().getProvider());
            delegate = dashScopeChatGateway;
        }
        return delegate.completeWithTools(messages, toolCallbacks, effectiveModel(requestedModel, aiProperties.getAgent().getModel()));
    }

    @Override
    public boolean supportsToolCalling() {
        return true;
    }

    private ChatModelGateway chatDelegate() {
        return delegateFor(aiProperties.getChat().getProvider());
    }

    private ChatModelGateway agentDelegate() {
        return delegateFor(aiProperties.getAgent().getProvider());
    }

    private ChatModelGateway delegateFor(String provider) {
        String normalized = provider == null ? "dashscope" : provider.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ollama" -> ollamaChatGateway;
            case "dashscope" -> dashScopeChatGateway;
            default -> {
                log.warn("Unknown AI provider '{}', fallback to DashScope", provider);
                yield dashScopeChatGateway;
            }
        };
    }

    private String effectiveModel(String requestedModel, String fallbackModel) {
        if (!aiProperties.isUseRequestedModel()) {
            return fallbackModel;
        }
        String normalized = requestedModel == null ? "" : requestedModel.trim();
        return normalized.isEmpty() ? fallbackModel : normalized;
    }
}
