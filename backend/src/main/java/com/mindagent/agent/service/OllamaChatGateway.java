package com.mindagent.agent.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class OllamaChatGateway implements ChatModelGateway {

    @Override
    public Flux<String> streamChat(List<Map<String, Object>> messages, String requestedModel) {
        return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ollama chat gateway is not enabled in this build"));
    }

    @Override
    public Mono<String> completeOnce(List<Map<String, Object>> messages, String requestedModel) {
        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ollama chat gateway is not enabled in this build"));
    }
}
