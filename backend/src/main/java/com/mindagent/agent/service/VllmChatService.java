package com.mindagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindagent.agent.config.VllmProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.ai.tool.ToolCallback;
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
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Override
    public Mono<ToolChatResult> completeWithTools(List<Map<String, Object>> messages,
                                                  List<ToolCallback> toolCallbacks,
                                                  String requestedModel) {
        return Mono.fromCallable(() -> runToolChat(messages, toolCallbacks, requestedModel))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .onErrorMap(this::wrapUpstreamError);
    }

    @Override
    public boolean supportsToolCalling() {
        return true;
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

    private ToolChatResult runToolChat(List<Map<String, Object>> messages,
                                       List<ToolCallback> toolCallbacks,
                                       String requestedModel) {
        List<Map<String, Object>> workingMessages = new ArrayList<>(messages);
        List<ToolExecutionView> executions = new ArrayList<>();
        Map<String, ToolCallback> callbacks = toolCallbacks.stream()
                .collect(Collectors.toMap(callback -> callback.getToolDefinition().name(), Function.identity()));

        for (int round = 0; round < 4; round++) {
            Map<String, Object> payload = buildPayload(workingMessages, requestedModel, false);
            payload.put("tools", toolCallbacks.stream().map(this::toOpenAiTool).toList());
            payload.put("tool_choice", "auto");

            String raw = postChatCompletion(payload);
            JsonNode message = firstMessage(raw);
            JsonNode toolCalls = message.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                return new ToolChatResult(message.path("content").asText(""), executions);
            }

            workingMessages.add(assistantToolCallMessage(message));
            for (JsonNode toolCall : toolCalls) {
                ToolExecutionView execution = executeToolCall(callbacks, toolCall);
                executions.add(execution);
                workingMessages.add(toolResultMessage(toolCall.path("id").asText(), execution.result()));
            }
        }
        return new ToolChatResult(summarizeToolExecutions(executions), executions);
    }

    private String postChatCompletion(Map<String, Object> payload) {
        return llmWebClient.post()
                .uri(vllmProperties.getChatPath())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> applyAuthHeader(headers, vllmProperties.getApiKey()))
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .blockOptional()
                .orElse("");
    }

    private JsonNode firstMessage(String raw) {
        try {
            return objectMapper.readTree(raw).path("choices").path(0).path("message");
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid chat completion response", ex);
        }
    }

    private Map<String, Object> toOpenAiTool(ToolCallback callback) {
        Map<String, Object> function = new HashMap<>();
        function.put("name", callback.getToolDefinition().name());
        function.put("description", callback.getToolDefinition().description());
        function.put("parameters", parseSchema(callback.getToolDefinition().inputSchema()));
        return Map.of("type", "function", "function", function);
    }

    private Object parseSchema(String schema) {
        try {
            return objectMapper.readValue(schema, Object.class);
        } catch (Exception ex) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    private Map<String, Object> assistantToolCallMessage(JsonNode message) {
        Map<String, Object> assistant = new HashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", message.path("content").isMissingNode() || message.path("content").isNull() ? "" : message.path("content").asText(""));
        assistant.put("tool_calls", objectMapper.convertValue(message.path("tool_calls"), Object.class));
        return assistant;
    }

    private ToolExecutionView executeToolCall(Map<String, ToolCallback> callbacks, JsonNode toolCall) {
        String name = toolCall.path("function").path("name").asText("");
        String arguments = toolCall.path("function").path("arguments").asText("{}");
        ToolCallback callback = callbacks.get(name);
        if (callback == null) {
            return new ToolExecutionView(name, arguments, "Unknown tool: " + name, "FAILED");
        }
        try {
            String result = callback.call(arguments);
            return new ToolExecutionView(name, arguments, result, "SUCCESS");
        } catch (RuntimeException ex) {
            return new ToolExecutionView(name, arguments, ex.getMessage() == null ? "tool_call_failed" : ex.getMessage(), "FAILED");
        }
    }

    private Map<String, Object> toolResultMessage(String toolCallId, String result) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("content", result == null ? "" : result);
        return message;
    }

    private String summarizeToolExecutions(List<ToolExecutionView> executions) {
        if (executions.isEmpty()) {
            return "";
        }
        ToolExecutionView last = executions.get(executions.size() - 1);
        return last.result() == null ? "" : last.result();
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
