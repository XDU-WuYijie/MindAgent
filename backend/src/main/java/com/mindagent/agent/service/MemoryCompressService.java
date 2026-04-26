package com.mindagent.agent.service;

import com.mindagent.agent.config.ChatMemoryProperties;
import com.mindagent.agent.entity.ChatMemoryCompressLog;
import com.mindagent.agent.entity.ChatMessage;
import com.mindagent.agent.entity.ChatSessionMemory;
import com.mindagent.agent.repository.ChatMemoryCompressLogRepository;
import com.mindagent.agent.repository.ChatSessionMemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MemoryCompressService {

    private static final String SUMMARY_PROMPT = """
            You maintain a long-term conversation summary.
            Update the summary using the previous summary and new messages.
            Keep stable facts, important goals, emotional state, unresolved questions, and commitments.
            Target around %d tokens and never exceed %d tokens.
            """;

    private final ChatMemoryProperties memoryProperties;
    private final ChatMessageService chatMessageService;
    private final ChatSessionMemoryRepository memoryRepository;
    private final ChatMemoryCompressLogRepository compressLogRepository;
    private final ChatModelGateway chatModelGateway;
    private final TokenEstimateService tokenEstimateService;
    private final RecentMemoryCacheService recentMemoryCacheService;

    public MemoryCompressService(ChatMemoryProperties memoryProperties,
                                 ChatMessageService chatMessageService,
                                 ChatSessionMemoryRepository memoryRepository,
                                 ChatMemoryCompressLogRepository compressLogRepository,
                                 ChatModelGateway chatModelGateway,
                                 TokenEstimateService tokenEstimateService,
                                 RecentMemoryCacheService recentMemoryCacheService) {
        this.memoryProperties = memoryProperties;
        this.chatMessageService = chatMessageService;
        this.memoryRepository = memoryRepository;
        this.compressLogRepository = compressLogRepository;
        this.chatModelGateway = chatModelGateway;
        this.tokenEstimateService = tokenEstimateService;
        this.recentMemoryCacheService = recentMemoryCacheService;
    }

    public void compressIfNeededAsync(Long userId, Long sessionId, String requestedModel) {
        Mono.fromCallable(() -> chatMessageService.listUncompressedMessages(userId, sessionId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(messages -> {
                    if (!shouldCompress(messages)) {
                        return Mono.empty();
                    }
                    List<ChatMessage> candidates = messages.subList(0, Math.max(0, messages.size() - keepMessageCount()));
                    if (candidates.isEmpty()) {
                        return Mono.empty();
                    }
                    ChatSessionMemory existing = memoryRepository.findBySessionIdAndUserId(sessionId, userId).orElse(null);
                    String oldSummary = existing == null ? "" : existing.getSummaryText();
                    List<Map<String, Object>> prompt = List.of(
                            ChatMessageFactory.message(
                                    "system",
                                    SUMMARY_PROMPT.formatted(
                                            memoryProperties.getSummaryTargetTokens(),
                                            memoryProperties.getSummaryHardLimitTokens()
                                    )
                            ),
                            ChatMessageFactory.message("user", buildSummaryInput(oldSummary, candidates))
                    );
                    return chatModelGateway.completeOnce(prompt, requestedModel)
                            .defaultIfEmpty(oldSummary)
                            .flatMap(summary -> persistCompressed(userId, sessionId, existing, candidates, summary));
                })
                .onErrorResume(ex -> Mono.empty())
                .subscribe();
    }

    private boolean shouldCompress(List<ChatMessage> messages) {
        if (messages.size() <= keepMessageCount()) {
            return false;
        }
        int tokens = messages.stream().mapToInt(this::safeTokenCount).sum();
        return tokens > memoryProperties.getCompressTriggerTokens()
                || messages.size() >= keepMessageCount() * 2;
    }

    private int keepMessageCount() {
        return Math.max(1, memoryProperties.getRecentKeepTurns() * 2);
    }

    private String buildSummaryInput(String oldSummary, List<ChatMessage> candidates) {
        StringBuilder builder = new StringBuilder();
        builder.append("Previous summary:\n");
        builder.append(oldSummary == null ? "" : oldSummary);
        builder.append("\n\nNew messages:\n");
        for (ChatMessage message : candidates) {
            builder.append(message.getRole()).append(": ").append(message.getContent()).append('\n');
        }
        return builder.toString();
    }

    @Transactional
    protected Mono<Void> persistCompressed(Long userId,
                                           Long sessionId,
                                           ChatSessionMemory existing,
                                           List<ChatMessage> candidates,
                                           String summary) {
        return Mono.fromRunnable(() -> {
                    ChatSessionMemory memory = existing == null ? new ChatSessionMemory() : existing;
                    memory.setSessionId(sessionId);
                    memory.setUserId(userId);
                    memory.setSummaryText(summary == null ? "" : summary);
                    memory.setSummaryTokenCount(tokenEstimateService.estimate(summary));
                    memory.setSummarizedUntilMessageId(candidates.get(candidates.size() - 1).getId());
                    memory.setVersion((existing == null ? 0 : existing.getVersion()) + 1);
                    memory.setUpdatedAt(LocalDateTime.now());
                    memoryRepository.save(memory);

                    List<Long> ids = candidates.stream().map(ChatMessage::getId).toList();
                    chatMessageService.markCompressed(ids);

                    ChatMemoryCompressLog log = new ChatMemoryCompressLog();
                    log.setSessionId(sessionId);
                    log.setUserId(userId);
                    log.setFromMessageId(candidates.get(0).getId());
                    log.setToMessageId(candidates.get(candidates.size() - 1).getId());
                    log.setSourceMessageCount(candidates.size());
                    log.setSourceTokenCount(candidates.stream().mapToInt(this::safeTokenCount).sum());
                    log.setSummaryVersion(memory.getVersion());
                    log.setStatus("SUCCESS");
                    compressLogRepository.save(log);

                    recentMemoryCacheService.rebuildFromDb(userId, sessionId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private int safeTokenCount(ChatMessage message) {
        return message.getTokenCount() == null ? 0 : message.getTokenCount();
    }
}
