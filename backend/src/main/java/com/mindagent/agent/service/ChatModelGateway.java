package com.mindagent.agent.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.ToolCallback;

public interface ChatModelGateway {

    Flux<String> streamChat(List<Map<String, Object>> messages, String requestedModel);

    Mono<String> completeOnce(List<Map<String, Object>> messages, String requestedModel);

    default Mono<ToolChatResult> completeWithTools(List<Map<String, Object>> messages,
                                                   List<ToolCallback> toolCallbacks,
                                                   String requestedModel) {
        return Mono.error(new UnsupportedOperationException("tool calling is not supported"));
    }

    default boolean supportsToolCalling() {
        return false;
    }
}

