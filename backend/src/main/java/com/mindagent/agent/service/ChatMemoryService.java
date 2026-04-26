package com.mindagent.agent.service;

import com.mindagent.agent.config.ChatMemoryProperties;
import com.mindagent.agent.dto.ChatSessionTokenDebugResponse;
import com.mindagent.agent.entity.ChatMessage;
import com.mindagent.agent.entity.ChatSessionMemory;
import com.mindagent.agent.repository.ChatSessionMemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatMemoryService {

    private final ChatMemoryProperties memoryProperties;
    private final ChatSessionMemoryRepository chatSessionMemoryRepository;
    private final RecentMemoryCacheService recentMemoryCacheService;
    private final ChatMessageService chatMessageService;
    private final TokenEstimateService tokenEstimateService;

    public ChatMemoryService(ChatMemoryProperties memoryProperties,
                             ChatSessionMemoryRepository chatSessionMemoryRepository,
                             RecentMemoryCacheService recentMemoryCacheService,
                             ChatMessageService chatMessageService,
                             TokenEstimateService tokenEstimateService) {
        this.memoryProperties = memoryProperties;
        this.chatSessionMemoryRepository = chatSessionMemoryRepository;
        this.recentMemoryCacheService = recentMemoryCacheService;
        this.chatMessageService = chatMessageService;
        this.tokenEstimateService = tokenEstimateService;
    }

    @Transactional(readOnly = true)
    public PromptMemory loadPromptMemory(Long userId, Long sessionId, int reservedTokens) {
        ChatSessionMemory summary = chatSessionMemoryRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElse(null);
        int summaryTokens = summary == null || summary.getSummaryTokenCount() == null ? 0 : summary.getSummaryTokenCount();
        int recentBudget = Math.max(256, memoryProperties.getInputBudget() - reservedTokens - summaryTokens);
        List<RecentMemoryMessage> recentRows = recentMemoryCacheService.loadRecentWindow(userId, sessionId, recentBudget);
        List<Map<String, Object>> promptRows = chatMessageService.toPromptMessages(recentRows);
        return new PromptMemory(summary == null ? "" : summary.getSummaryText(), summaryTokens, promptRows, recentRows);
    }

    @Transactional(readOnly = true)
    public ChatSessionTokenDebugResponse buildTokenDebug(Long userId, Long sessionId, String draftQuery) {
        List<ChatMessage> allMessages = chatMessageService.listSessionMessageEntities(userId, sessionId);
        ChatSessionMemory summary = chatSessionMemoryRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElse(null);
        int summaryTokens = summary == null || summary.getSummaryTokenCount() == null ? 0 : summary.getSummaryTokenCount();
        int draftTokens = tokenEstimateService.estimate(draftQuery);
        int recentBudget = Math.max(256, memoryProperties.getInputBudget() - draftTokens - summaryTokens);
        RecentMemoryCacheService.RecentWindowSnapshot recentSnapshot = recentMemoryCacheService
                .loadRecentWindowSnapshot(userId, sessionId, recentBudget);

        ChatSessionTokenDebugResponse response = new ChatSessionTokenDebugResponse();
        response.setSessionId(sessionId);
        response.setDraftQuery(draftQuery == null ? "" : draftQuery);
        response.setDraftQueryTokens(draftTokens);
        response.setEstimatedPromptTokens(summaryTokens + draftTokens + sumRecentTokens(recentSnapshot.messages()));
        response.setInputBudget(memoryProperties.getInputBudget());
        response.setRecentBudget(recentBudget);
        response.setRecentKeepTurns(memoryProperties.getRecentKeepTurns());
        response.setCompressTriggerTokens(memoryProperties.getCompressTriggerTokens());
        response.setSummaryTargetTokens(memoryProperties.getSummaryTargetTokens());
        response.setSummaryHardLimitTokens(memoryProperties.getSummaryHardLimitTokens());
        response.setRagBudget(memoryProperties.getRagBudget());
        response.setMinRagBudget(memoryProperties.getMinRagBudget());
        response.setSummary(toSummary(summary));
        response.setRecentWindow(toRecentWindow(recentSnapshot));
        response.setTotalMessages(toMessageStats(allMessages, null));
        response.setCompressedMessages(toMessageStats(allMessages, true));
        response.setUncompressedMessages(toMessageStats(allMessages, false));
        return response;
    }

    public List<Map<String, Object>> buildMessages(IntentType intent,
                                                   String query,
                                                   List<String> ragContexts,
                                                   PromptMemory memory) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(ChatMessageFactory.message("system", responseSystemPrompt(intent, ragContexts)));
        if (memory.summaryText() != null && !memory.summaryText().isBlank()) {
            messages.add(ChatMessageFactory.message("system", "Session summary:\n" + memory.summaryText()));
        }
        messages.addAll(memory.recentPromptMessages());
        messages.add(ChatMessageFactory.message("user", query));
        return messages;
    }

    public record PromptMemory(
            String summaryText,
            int summaryTokens,
            List<Map<String, Object>> recentPromptMessages,
            List<RecentMemoryMessage> recentMessages
    ) {
    }

    private ChatSessionTokenDebugResponse.SessionSummary toSummary(ChatSessionMemory summary) {
        ChatSessionTokenDebugResponse.SessionSummary item = new ChatSessionTokenDebugResponse.SessionSummary();
        item.setPresent(summary != null);
        if (summary == null) {
            item.setTokenCount(0);
            item.setVersion(0);
            item.setPreview("");
            return item;
        }
        item.setTokenCount(summary.getSummaryTokenCount() == null ? 0 : summary.getSummaryTokenCount());
        item.setVersion(summary.getVersion() == null ? 0 : summary.getVersion());
        item.setSummarizedUntilMessageId(summary.getSummarizedUntilMessageId());
        item.setPreview(limit(summary.getSummaryText(), 240));
        return item;
    }

    private ChatSessionTokenDebugResponse.RecentWindow toRecentWindow(RecentMemoryCacheService.RecentWindowSnapshot snapshot) {
        ChatSessionTokenDebugResponse.RecentWindow item = new ChatSessionTokenDebugResponse.RecentWindow();
        item.setSource(snapshot.source());
        item.setMessageCount(snapshot.messages().size());
        item.setTokenCount(sumRecentTokens(snapshot.messages()));
        if (!snapshot.messages().isEmpty()) {
            item.setFirstMessageId(snapshot.messages().get(0).messageId());
            item.setLastMessageId(snapshot.messages().get(snapshot.messages().size() - 1).messageId());
        }
        return item;
    }

    private ChatSessionTokenDebugResponse.MessageStats toMessageStats(List<ChatMessage> messages, Boolean compressed) {
        ChatSessionTokenDebugResponse.MessageStats item = new ChatSessionTokenDebugResponse.MessageStats();
        int count = 0;
        int tokens = 0;
        for (ChatMessage message : messages) {
            if (compressed != null && !compressed.equals(message.getCompressed())) {
                continue;
            }
            count++;
            tokens += safeTokenCount(message.getTokenCount());
        }
        item.setMessageCount(count);
        item.setTokenCount(tokens);
        return item;
    }

    private int sumRecentTokens(List<RecentMemoryMessage> messages) {
        int total = 0;
        for (RecentMemoryMessage message : messages) {
            total += safeTokenCount(message.tokenCount());
        }
        return total;
    }

    private int safeTokenCount(Integer tokenCount) {
        return tokenCount == null || tokenCount < 0 ? 0 : tokenCount;
    }

    private String limit(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "...";
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
        return "You are a friendly chat assistant. Reply naturally and concisely.";
    }
}
