package com.mindagent.agent.service;

import com.mindagent.agent.dto.ChatSessionMessageItem;
import com.mindagent.agent.entity.ChatMessage;
import com.mindagent.agent.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final TokenEstimateService tokenEstimateService;

    public ChatMessageService(ChatMessageRepository chatMessageRepository,
                              TokenEstimateService tokenEstimateService) {
        this.chatMessageRepository = chatMessageRepository;
        this.tokenEstimateService = tokenEstimateService;
    }

    @Transactional
    public ChatMessage saveMessage(Long userId, Long sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setUserId(userId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setTokenCount(tokenEstimateService.estimate(content));
        message.setMessageStatus("COMPLETED");
        message.setCompressed(false);
        message.setMetadata("{}");
        return chatMessageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionMessageItem> listSessionMessages(Long userId, Long sessionId) {
        return chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAscIdAsc(sessionId, userId)
                .stream()
                .map(this::toMessageItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecentMemoryMessage> loadRecentWindowFromDb(Long userId, Long sessionId, int tokenBudget, int keepTurns) {
        List<ChatMessage> rows = chatMessageRepository
                .findTop100BySessionIdAndUserIdAndCompressedFalseOrderByCreatedAtDescIdDesc(sessionId, userId);
        Collections.reverse(rows);
        return trimRecentWindow(rows, tokenBudget, keepTurns);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> listUncompressedMessages(Long userId, Long sessionId) {
        List<ChatMessage> rows = chatMessageRepository
                .findTop100BySessionIdAndUserIdAndCompressedFalseOrderByCreatedAtDescIdDesc(sessionId, userId);
        Collections.reverse(rows);
        return rows;
    }

    @Transactional(readOnly = true)
    public long countUncompressedMessages(Long userId, Long sessionId) {
        return chatMessageRepository.countBySessionIdAndUserIdAndCompressedFalse(sessionId, userId);
    }

    @Transactional
    public void markCompressed(List<Long> ids) {
        if (!ids.isEmpty()) {
            chatMessageRepository.markCompressedByIds(ids);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> toPromptMessages(List<RecentMemoryMessage> rows) {
        return rows.stream()
                .map(row -> ChatMessageFactory.message(row.role(), row.content()))
                .toList();
    }

    private List<RecentMemoryMessage> trimRecentWindow(List<ChatMessage> rows, int tokenBudget, int keepTurns) {
        if (rows.isEmpty()) {
            return List.of();
        }
        int hardMessageLimit = Math.max(keepTurns * 2, 1);
        List<ChatMessage> limited = rows;
        if (rows.size() > hardMessageLimit) {
            limited = rows.subList(rows.size() - hardMessageLimit, rows.size());
        }
        List<RecentMemoryMessage> selected = new ArrayList<>();
        int usedTokens = 0;
        for (int i = limited.size() - 1; i >= 0; i--) {
            ChatMessage row = limited.get(i);
            int next = usedTokens + safeTokenCount(row);
            if (!selected.isEmpty() && tokenBudget > 0 && next > tokenBudget) {
                break;
            }
            usedTokens = next;
            selected.add(new RecentMemoryMessage(
                    row.getId(),
                    row.getRole(),
                    row.getContent(),
                    row.getTokenCount(),
                    row.getCreatedAt()
            ));
        }
        Collections.reverse(selected);
        return selected;
    }

    private int safeTokenCount(ChatMessage message) {
        return message.getTokenCount() == null || message.getTokenCount() < 0 ? 0 : message.getTokenCount();
    }

    private ChatSessionMessageItem toMessageItem(ChatMessage row) {
        ChatSessionMessageItem item = new ChatSessionMessageItem();
        item.setMessageId(row.getId());
        item.setRole(row.getRole());
        item.setContent(row.getContent());
        item.setTokenCount(row.getTokenCount());
        item.setMessageStatus(row.getMessageStatus());
        item.setCompressed(row.getCompressed());
        item.setCreatedAt(row.getCreatedAt());
        return item;
    }
}
