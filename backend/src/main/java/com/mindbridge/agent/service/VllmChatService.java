package com.mindbridge.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridge.agent.config.VllmProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class VllmChatService implements ChatModelGateway {

    private final WebClient llmWebClient;
    private final VllmProperties vllmProperties;
    private final ObjectMapper objectMapper;

    public VllmChatService(WebClient llmWebClient,
                           VllmProperties vllmProperties,
                           ObjectMapper objectMapper) {
        this.llmWebClient = llmWebClient;
        this.vllmProperties = vllmProperties;
        this.objectMapper = objectMapper;
    }

    public Flux<String> streamChat(String query, String requestedModel) {
        return streamChat(List.of(message("user", query)), requestedModel);
    }

    @Override
    public Flux<String> streamChat(List<Map<String, Object>> messages, String requestedModel) {
        Map<String, Object> payload = buildPayload(messages, requestedModel, true);
        return llmWebClient.post()
            .uri(vllmProperties.getChatPath())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .headers(headers -> applyAuthHeader(headers, vllmProperties.getApiKey()))
            .bodyValue(payload)
            .retrieve()
            .bodyToFlux(String.class)
            .flatMap(this::extractResponseTextFromChunk)
            .filter(token -> !token.isEmpty())
            .onErrorMap(this::wrapUpstreamError);
    }

    @Override
    public Mono<String> completeOnce(List<Map<String, Object>> messages, String requestedModel) {
        Map<String, Object> payload = buildPayload(messages, requestedModel, false);
        return llmWebClient.post()
            .uri(vllmProperties.getChatPath())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers(headers -> applyAuthHeader(headers, vllmProperties.getApiKey()))
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(String.class)
            .map(this::extractMessageContent)
            .defaultIfEmpty("")
            .onErrorMap(this::wrapUpstreamError);
    }

    private void applyAuthHeader(HttpHeaders headers, String apiKey) {
        if (apiKey != null && !apiKey.isBlank() && !"EMPTY".equalsIgnoreCase(apiKey.trim())) {
            headers.setBearerAuth(apiKey.trim());
        }
    }

    private Throwable wrapUpstreamError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            return new RuntimeException(
                    "vLLM request failed: status=" + ex.getStatusCode().value() + ", body=" + body,
                    ex
            );
        }
        return throwable;
    }

    private Flux<String> extractResponseTextFromChunk(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return Flux.empty();
        }

        String[] lines = chunk.split("\\r?\\n");
        List<String> tokens = new ArrayList<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("data:")) {
                line = line.substring(5).trim();
            }

            if (line.isEmpty() || "[DONE]".equals(line)) {
                continue;
            }

            parseToken(line).ifPresent(tokens::add);
        }

        return Flux.fromIterable(tokens);
    }

    private Optional<String> parseToken(String jsonLine) {
        try {
            JsonNode node = objectMapper.readTree(jsonLine);
            JsonNode contentNode = node.path("choices").path(0).path("delta").path("content");
            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                return Optional.of(contentNode.asText(""));
            }
        } catch (Exception ignored) {
            // Ignore malformed stream fragments and continue consuming.
        }
        return Optional.empty();
    }

    private String extractMessageContent(String jsonText) {
        try {
            JsonNode node = objectMapper.readTree(jsonText);
            JsonNode contentNode = node.path("choices").path(0).path("message").path("content");
            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                return contentNode.asText("");
            }
        } catch (Exception ignored) {
            // Ignore parse errors and return an empty string.
        }
        return "";
    }

    private Map<String, Object> buildPayload(List<Map<String, Object>> messages,
                                             String requestedModel,
                                             boolean stream) {
        String model = requestedModel == null || requestedModel.trim().isEmpty()
            ? vllmProperties.getModel()
            : requestedModel.trim();
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", stream);
        return payload;
    }

    public static Map<String, Object> message(String role, String content) {
        return ChatMessageFactory.message(role, content);
    }
}
