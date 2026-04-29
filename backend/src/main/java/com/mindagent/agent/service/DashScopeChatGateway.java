package com.mindagent.agent.service;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class DashScopeChatGateway implements ChatModelGateway {

    private final ObjectProvider<SpringAiChatService> springAiChatServiceProvider;
    private final VllmChatService vllmChatService;

    public DashScopeChatGateway(ObjectProvider<SpringAiChatService> springAiChatServiceProvider,
                                VllmChatService vllmChatService) {
        this.springAiChatServiceProvider = springAiChatServiceProvider;
        this.vllmChatService = vllmChatService;
    }

    @Override
    public Flux<String> streamChat(List<Map<String, Object>> messages, String requestedModel) {
        SpringAiChatService delegate = springAiChatServiceProvider.getIfAvailable();
        if (delegate != null) {
            return delegate.streamChat(messages, requestedModel);
        }
        return vllmChatService.streamChat(messages, requestedModel);
    }

    @Override
    public Mono<String> completeOnce(List<Map<String, Object>> messages, String requestedModel) {
        SpringAiChatService delegate = springAiChatServiceProvider.getIfAvailable();
        if (delegate != null) {
            return delegate.completeOnce(messages, requestedModel);
        }
        return vllmChatService.completeOnce(messages, requestedModel);
    }

    @Override
    public Mono<ToolChatResult> completeWithTools(List<Map<String, Object>> messages,
                                                  List<ToolCallback> toolCallbacks,
                                                  String requestedModel) {
        return requireDelegate().completeWithTools(messages, toolCallbacks, requestedModel);
    }

    @Override
    public boolean supportsToolCalling() {
        return true;
    }

    private SpringAiChatService requireDelegate() {
        SpringAiChatService delegate = springAiChatServiceProvider.getIfAvailable();
        if (delegate == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Spring AI ChatModel is not available. Check spring.ai.openai configuration and auto-configuration."
            );
        }
        return delegate;
    }
}
