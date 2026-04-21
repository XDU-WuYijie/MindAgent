package com.mindbridge.agent.service;

import com.mindbridge.agent.config.RagProperties;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ChatOrchestrationService {

    private static final String INTENT_PROMPT = """
            You are an intent classifier and must output only one label: CHAT, CONSULT, or RISK.
            Rules:
            - CHAT: casual small talk, greetings, weather, daily life, unrelated chit-chat.
            - CONSULT: psychological stress, anxiety, low mood, sleep, relationship or study pressure.
            - RISK: self-harm, suicide intent, severe hopelessness, harming others.
            Output exactly one token: CHAT or CONSULT or RISK.
            """;

    private final ChatModelGateway chatModelGateway;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RagProperties ragProperties;
    private final ConversationService conversationService;
    private final McpDispatchService mcpDispatchService;

    public ChatOrchestrationService(ChatModelGateway chatModelGateway,
                                    KnowledgeBaseService knowledgeBaseService,
                                    RagProperties ragProperties,
                                    ConversationService conversationService,
                                    McpDispatchService mcpDispatchService) {
        this.chatModelGateway = chatModelGateway;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ragProperties = ragProperties;
        this.conversationService = conversationService;
        this.mcpDispatchService = mcpDispatchService;
    }

    public Flux<ServerSentEvent<String>> streamChatWithIntent(Long userId,
                                                              String conversationId,
                                                              String query,
                                                              String requestedModel) {
        return classifyIntent(query, requestedModel)
                .flatMapMany(intent -> conversationService.loadRecentHistory(userId, conversationId)
                        .flatMapMany(history -> {
                    List<String> contexts = shouldUseRag(intent)
                            ? knowledgeBaseService.retrieve(query, ragProperties.getTopK())
                            : List.of();
                    List<Map<String, Object>> messages = buildMessages(intent, query, contexts, history);
                    Flux<ServerSentEvent<String>> intentEvent = Flux.just(ServerSentEvent.<String>builder()
                            .event("intent")
                            .data(intent.name())
                            .build());
                    Flux<ServerSentEvent<String>> ragEvent = Flux.just(ServerSentEvent.<String>builder()
                            .event("rag")
                            .data("contexts=" + contexts.size())
                            .build());

                    StringBuilder answerBuffer = new StringBuilder();
                    Flux<ServerSentEvent<String>> answerEvents = chatModelGateway.streamChat(messages, requestedModel)
                            .map(chunk -> {
                                answerBuffer.append(chunk);
                                return ServerSentEvent.<String>builder()
                                        .event("token")
                                        .data(chunk)
                                        .build();
                            })
                            .concatWithValues(ServerSentEvent.<String>builder()
                                    .event("done")
                                    .data("[DONE]")
                                    .build())
                            .doOnComplete(() -> {
                                conversationService.saveConversationAsync(
                                        userId,
                                        conversationId,
                                        query,
                                        answerBuffer.toString(),
                                        intent
                                );
                                conversationService.saveReportAsync(userId, query, intent, contexts.size());
                                mcpDispatchService.dispatchAsync(
                                        userId,
                                        query,
                                        intent,
                                        toRiskLevel(intent),
                                        contexts.size()
                                );
                            })
                            .onErrorResume(ex -> Flux.just(
                                    ServerSentEvent.<String>builder()
                                            .event("error")
                                            .data(ex.getMessage() == null ? "chat_stream_failed" : ex.getMessage())
                                            .build(),
                                    ServerSentEvent.<String>builder()
                                            .event("done")
                                            .data("[DONE]")
                                            .build()
                            ));

                    return intentEvent.concatWith(ragEvent).concatWith(answerEvents);
                }))
                .onErrorResume(ex -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("error")
                                .data(ex.getMessage() == null ? "chat_orchestration_failed" : ex.getMessage())
                                .build(),
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("[DONE]")
                                .build()
                ));
    }

    private Mono<IntentType> classifyIntent(String query, String requestedModel) {
        List<Map<String, Object>> messages = List.of(
                ChatMessageFactory.message("system", INTENT_PROMPT),
                ChatMessageFactory.message("user", query)
        );
        return chatModelGateway.completeOnce(messages, requestedModel)
                .map(result -> {
                    String normalized = result == null ? "" : result.trim().toUpperCase(Locale.ROOT);
                    if (normalized.contains("RISK")) {
                        return IntentType.RISK;
                    }
                    if (normalized.contains("CONSULT")) {
                        return IntentType.CONSULT;
                    }
                    return IntentType.CHAT;
                })
                .defaultIfEmpty(IntentType.CHAT);
    }

    private List<Map<String, Object>> buildMessages(IntentType intent,
                                                    String query,
                                                    List<String> contexts,
                                                    List<Map<String, Object>> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(ChatMessageFactory.message("system", responseSystemPrompt(intent, contexts)));
        messages.addAll(history);
        messages.add(ChatMessageFactory.message("user", query));
        return messages;
    }

    private String responseSystemPrompt(IntentType intent, List<String> contexts) {
        String contextBlock = contexts.isEmpty()
                ? ""
                : "\nUse the following retrieved knowledge when relevant:\n" + String.join("\n---\n", contexts);
        if (intent == IntentType.RISK) {
            return """
                    You are a campus mental-health assistant.
                    The user may be high-risk. Respond with empathy, calm tone, and concise actionable support.
                    Encourage contacting trusted people and professional help quickly.
                    Never provide harmful guidance.
                    """ + contextBlock;
        }
        if (intent == IntentType.CONSULT) {
            return """
                    You are a campus mental-health assistant.
                    Respond with empathy and structure: acknowledge feelings first, then provide 1-3 practical suggestions.
                    Keep it concise and supportive.
                    """ + contextBlock;
        }
        return """
                You are a friendly chat assistant. Reply naturally and concisely.
                """;
    }

    private boolean shouldUseRag(IntentType intent) {
        return ragProperties.isEnabled() && (intent == IntentType.CONSULT || intent == IntentType.RISK);
    }

    private String toRiskLevel(IntentType intent) {
        if (intent == IntentType.RISK) {
            return "high";
        }
        if (intent == IntentType.CONSULT) {
            return "medium";
        }
        return "low";
    }
}
