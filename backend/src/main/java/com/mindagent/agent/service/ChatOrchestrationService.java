package com.mindagent.agent.service;

import com.mindagent.agent.config.ChatMemoryProperties;
import com.mindagent.agent.config.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindagent.agent.entity.ChatMessage;
import com.mindagent.agent.entity.ChatSession;
import com.mindagent.agent.entity.PsychologicalReport;
import com.mindagent.agent.repository.PsychologicalReportRepository;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    private final RagProperties ragProperties;
    private final ChatMemoryProperties memoryProperties;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatMemoryService chatMemoryService;
    private final RecentMemoryCacheService recentMemoryCacheService;
    private final MemoryCompressService memoryCompressService;
    private final TokenEstimateService tokenEstimateService;
    private final McpDispatchService mcpDispatchService;
    private final PsychologicalReportRepository reportRepository;
    private final QueryRoutingService queryRoutingService;
    private final RagRetrievalService ragRetrievalService;
    private final ObjectMapper objectMapper;

    public ChatOrchestrationService(ChatModelGateway chatModelGateway,
                                    RagProperties ragProperties,
                                    ChatMemoryProperties memoryProperties,
                                    ChatSessionService chatSessionService,
                                    ChatMessageService chatMessageService,
                                    ChatMemoryService chatMemoryService,
                                    RecentMemoryCacheService recentMemoryCacheService,
                                    MemoryCompressService memoryCompressService,
                                    TokenEstimateService tokenEstimateService,
                                    McpDispatchService mcpDispatchService,
                                    PsychologicalReportRepository reportRepository,
                                    QueryRoutingService queryRoutingService,
                                    RagRetrievalService ragRetrievalService,
                                    ObjectMapper objectMapper) {
        this.chatModelGateway = chatModelGateway;
        this.ragProperties = ragProperties;
        this.memoryProperties = memoryProperties;
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.chatMemoryService = chatMemoryService;
        this.recentMemoryCacheService = recentMemoryCacheService;
        this.memoryCompressService = memoryCompressService;
        this.tokenEstimateService = tokenEstimateService;
        this.mcpDispatchService = mcpDispatchService;
        this.reportRepository = reportRepository;
        this.queryRoutingService = queryRoutingService;
        this.ragRetrievalService = ragRetrievalService;
        this.objectMapper = objectMapper;
    }

    public Flux<ServerSentEvent<String>> streamChatWithIntent(Long userId,
                                                              Long sessionId,
                                                              String query,
                                                              String requestedModel) {
        return classifyIntent(query, requestedModel)
                .flatMap(intent -> queryRoutingService.classify(query, requestedModel)
                        .map(queryType -> new RoutedQuery(intent, queryType)))
                .flatMapMany(routed -> Mono.fromCallable(() -> {
                            ChatSession session = chatSessionService.requireOwnedSession(userId, sessionId);
                            ChatMemoryService.PromptMemory promptMemory = chatMemoryService.loadPromptMemory(
                                    userId,
                                    sessionId,
                                    tokenEstimateService.estimate(query) + memoryReserve(routed.intent(), routed.queryType())
                            );
                            ChatMessage userMessage = chatMessageService.saveMessage(userId, sessionId, "user", query);
                            chatSessionService.touchSession(session, query);
                            return new PreparedChat(routed.intent(), routed.queryType(), promptMemory, userMessage);
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(prepared -> {
                    Mono<RagRetrievalResult> retrievalMono = shouldUseRag(prepared.intent(), prepared.queryType())
                            ? ragRetrievalService.retrieve(prepared.intent(), prepared.queryType(), query)
                            : Mono.just(new RagRetrievalResult(prepared.intent(), prepared.queryType(), query, List.of(), List.of(), List.of(), List.of(), List.of()));
                    return retrievalMono.flatMapMany(retrieval -> {
                        List<String> contexts = retrieval.selectedResults().stream()
                                .map(RetrievedChunk::content)
                                .toList();
                        List<Map<String, Object>> messages = chatMemoryService.buildMessages(
                                prepared.intent(),
                                prepared.queryType(),
                                query,
                                contexts,
                                prepared.promptMemory()
                        );
                        Flux<ServerSentEvent<String>> intentEvent = Flux.just(ServerSentEvent.<String>builder()
                                .event("intent")
                                .data(prepared.intent().name())
                                .build());
                        Flux<ServerSentEvent<String>> ragEvent = Flux.just(ServerSentEvent.<String>builder()
                                .event("rag")
                                .data(buildRagEventData(retrieval))
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
                                    persistAssistantReply(
                                            userId,
                                            sessionId,
                                            query,
                                            prepared.intent(),
                                            requestedModel,
                                            contexts,
                                            prepared.userMessage(),
                                            answerBuffer.toString()
                                    );
                                    mcpDispatchService.dispatchAsync(
                                            userId,
                                            query,
                                            prepared.intent(),
                                            toRiskLevel(prepared.intent()),
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
                    });
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

    public Mono<IntentType> classifyIntent(String query, String requestedModel) {
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

    public IntentType classifyIntentSync(String query, String requestedModel) {
        return classifyIntent(query, requestedModel).blockOptional().orElse(IntentType.CHAT);
    }

    private boolean shouldUseRag(IntentType intent) {
        return ragProperties.isEnabled() && (intent == IntentType.CONSULT || intent == IntentType.RISK);
    }

    private boolean shouldUseRag(IntentType intent, QueryType queryType) {
        return shouldUseRag(intent) && queryType != QueryType.OTHER;
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

    private int memoryReserve(IntentType intent, QueryType queryType) {
        return shouldUseRag(intent, queryType) ? Math.max(0, 1024 + memoryProperties.getMinRagBudget()) : 1024;
    }

    private String buildRagEventData(RagRetrievalResult retrieval) {
        try {
                    Map<String, Object> payload = Map.of(
                            "queryType", retrieval.queryType().name(),
                            "rewrittenQuery", retrieval.rewrittenQuery(),
                            "contexts", retrieval.selectedResults().size(),
                            "rerankTopK", retrieval.rerankResults().size(),
                            "rerankResults", retrieval.rerankResults(),
                            "selectedChunks", retrieval.selectedResults()
                    );
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"queryType\":\"" + retrieval.queryType().name() + "\",\"contexts\":" + retrieval.selectedResults().size() + "}";
        }
    }

    private void persistAssistantReply(Long userId,
                                       Long sessionId,
                                       String query,
                                       IntentType intent,
                                       String requestedModel,
                                       List<String> contexts,
                                       ChatMessage userMessage,
                                       String answer) {
        Mono.fromRunnable(() -> {
                    ChatMessage assistant = chatMessageService.saveMessage(userId, sessionId, "assistant", answer);
                    recentMemoryCacheService.refreshAfterAppend(
                            userId,
                            sessionId,
                            List.of(
                                    new RecentMemoryMessage(
                                            userMessage.getId(),
                                            "user",
                                            query,
                                            userMessage.getTokenCount(),
                                            userMessage.getCreatedAt()
                                    ),
                                    new RecentMemoryMessage(assistant.getId(), "assistant", answer, assistant.getTokenCount(), assistant.getCreatedAt())
                            )
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(ignored -> {
                    memoryCompressService.compressIfNeededAsync(userId, sessionId, requestedModel);
                    saveReportAsync(userId, query, intent, contexts.size());
                })
                .subscribe();
    }

    private void saveReportAsync(Long userId, String query, IntentType intent, int ragContexts) {
        Mono.fromRunnable(() -> {
                    PsychologicalReport report = new PsychologicalReport();
                    report.setUserId(userId);
                    report.setQueryText(query);
                    report.setIntent(intent.name());
                    report.setRiskLevel(toRiskLevel(intent));
                    report.setRagContexts(ragContexts);
                    reportRepository.save(report);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private record PreparedChat(
            IntentType intent,
            QueryType queryType,
            ChatMemoryService.PromptMemory promptMemory,
            ChatMessage userMessage
    ) {
    }

    private record RoutedQuery(
            IntentType intent,
            QueryType queryType
    ) {
    }
}
