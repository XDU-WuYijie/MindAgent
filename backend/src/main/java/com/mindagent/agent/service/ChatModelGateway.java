package com.mindagent.agent.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface ChatModelGateway {

    Flux<String> streamChat(List<Map<String, Object>> messages, String requestedModel);

    Mono<String> completeOnce(List<Map<String, Object>> messages, String requestedModel);
}

