package com.mindagent.agent.controller;

import com.mindagent.agent.dto.ChatStreamRequest;
import com.mindagent.agent.dto.ChatSessionListItem;
import com.mindagent.agent.dto.ChatSessionMessageItem;
import com.mindagent.agent.dto.CreateSessionResponse;
import com.mindagent.agent.service.ChatMessageService;
import com.mindagent.agent.service.ChatOrchestrationService;
import com.mindagent.agent.service.ChatSessionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
@Validated
public class ChatController {

    private final ChatOrchestrationService chatOrchestrationService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    public ChatController(ChatOrchestrationService chatOrchestrationService,
                          ChatSessionService chatSessionService,
                          ChatMessageService chatMessageService) {
        this.chatOrchestrationService = chatOrchestrationService;
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ChatStreamRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt, request.getUserId());
        return chatOrchestrationService.streamChatWithIntent(userId, request.getSessionId(), request.getQuery(), request.getModel());
    }

    @PostMapping("/chat/sessions")
    public CreateSessionResponse createSession(@AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt, 1L);
        return chatSessionService.createSession(userId);
    }

    @GetMapping("/chat/sessions")
    public List<ChatSessionListItem> listSessions(@AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt, 1L);
        return chatSessionService.listSessions(userId);
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    public List<ChatSessionMessageItem> listMessages(@PathVariable Long sessionId,
                                                     @AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt, 1L);
        chatSessionService.requireOwnedSession(userId, sessionId);
        return chatMessageService.listSessionMessages(userId, sessionId);
    }

    @DeleteMapping("/chat/sessions/{sessionId}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable Long sessionId,
                              @AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt, 1L);
        chatSessionService.deleteSession(userId, sessionId);
    }

    private Long resolveUserId(Jwt jwt, Long fallbackUserId) {
        Long defaultUserId = fallbackUserId == null ? 1L : fallbackUserId;
        if (jwt == null) {
            return defaultUserId;
        }
        Object claim = jwt.getClaim("uid");
        if (claim == null) {
            return defaultUserId;
        }
        if (claim instanceof Number number) {
            return number.longValue();
        }
        if (claim instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultUserId;
            }
        }
        return defaultUserId;
    }

}
