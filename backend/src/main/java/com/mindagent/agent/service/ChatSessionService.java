package com.mindagent.agent.service;

import com.mindagent.agent.dto.ChatSessionListItem;
import com.mindagent.agent.dto.CreateSessionResponse;
import com.mindagent.agent.entity.ChatSession;
import com.mindagent.agent.repository.ChatMemoryCompressLogRepository;
import com.mindagent.agent.repository.ChatMessageRepository;
import com.mindagent.agent.repository.ChatSessionMemoryRepository;
import com.mindagent.agent.repository.ChatSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatSessionService {

    private static final String DEFAULT_TITLE = "新会话";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionMemoryRepository chatSessionMemoryRepository;
    private final ChatMemoryCompressLogRepository compressLogRepository;
    private final RecentMemoryCacheService recentMemoryCacheService;

    public ChatSessionService(ChatSessionRepository chatSessionRepository,
                              ChatMessageRepository chatMessageRepository,
                              ChatSessionMemoryRepository chatSessionMemoryRepository,
                              ChatMemoryCompressLogRepository compressLogRepository,
                              RecentMemoryCacheService recentMemoryCacheService) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionMemoryRepository = chatSessionMemoryRepository;
        this.compressLogRepository = compressLogRepository;
        this.recentMemoryCacheService = recentMemoryCacheService;
    }

    @Transactional
    public CreateSessionResponse createSession(Long userId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(DEFAULT_TITLE);
        session.setLastMessageAt(LocalDateTime.now());
        ChatSession saved = chatSessionRepository.save(session);
        return toCreateResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionListItem> listSessions(Long userId) {
        return chatSessionRepository.findAllByUserIdOrderByLastMessageAtDescCreatedAtDesc(userId)
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatSession requireOwnedSession(Long userId, Long sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    @Transactional
    public void touchSession(ChatSession session, String firstUserMessage) {
        boolean useFirstMessage = session.getTitle() == null
                || session.getTitle().isBlank()
                || DEFAULT_TITLE.equals(session.getTitle());
        if (useFirstMessage && firstUserMessage != null && !firstUserMessage.isBlank()) {
            String title = firstUserMessage.trim();
            session.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
        }
        session.setLastMessageAt(LocalDateTime.now());
        chatSessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        chatMessageRepository.deleteBySessionIdAndUserId(sessionId, userId);
        chatSessionMemoryRepository.deleteBySessionIdAndUserId(sessionId, userId);
        compressLogRepository.deleteBySessionIdAndUserId(sessionId, userId);
        chatSessionRepository.deleteByIdAndUserId(sessionId, userId);
        recentMemoryCacheService.deleteRecentWindow(userId, sessionId);
    }

    private CreateSessionResponse toCreateResponse(ChatSession session) {
        CreateSessionResponse response = new CreateSessionResponse();
        response.setSessionId(session.getId());
        response.setTitle(session.getTitle());
        response.setCreatedAt(session.getCreatedAt());
        response.setLastMessageAt(session.getLastMessageAt());
        return response;
    }

    private ChatSessionListItem toListItem(ChatSession session) {
        ChatSessionListItem item = new ChatSessionListItem();
        item.setSessionId(session.getId());
        item.setTitle(session.getTitle());
        item.setCreatedAt(session.getCreatedAt());
        item.setLastMessageAt(session.getLastMessageAt());
        return item;
    }
}
