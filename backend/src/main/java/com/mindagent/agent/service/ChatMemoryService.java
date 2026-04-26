package com.mindagent.agent.service;

import com.mindagent.agent.config.ChatMemoryProperties;
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

    public ChatMemoryService(ChatMemoryProperties memoryProperties,
                             ChatSessionMemoryRepository chatSessionMemoryRepository,
                             RecentMemoryCacheService recentMemoryCacheService,
                             ChatMessageService chatMessageService) {
        this.memoryProperties = memoryProperties;
        this.chatSessionMemoryRepository = chatSessionMemoryRepository;
        this.recentMemoryCacheService = recentMemoryCacheService;
        this.chatMessageService = chatMessageService;
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
