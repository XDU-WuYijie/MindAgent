package com.mindagent.agent.controller;

import com.mindagent.agent.dto.ChatStreamRequest;
import com.mindagent.agent.config.ChatMemoryProperties;
import com.mindagent.agent.service.ChatOrchestrationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Validated
public class ChatController {

    private final ChatOrchestrationService chatOrchestrationService;
    private final ChatMemoryProperties chatMemoryProperties;

    public ChatController(ChatOrchestrationService chatOrchestrationService,
                          ChatMemoryProperties chatMemoryProperties) {
        this.chatOrchestrationService = chatOrchestrationService;
        this.chatMemoryProperties = chatMemoryProperties;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ChatStreamRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt, request.getUserId());
        String conversationId = resolveConversationId(request.getConversationId());
        return chatOrchestrationService.streamChatWithIntent(userId, conversationId, request.getQuery(), request.getModel());
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

    private String resolveConversationId(String conversationId) {
        String fallback = chatMemoryProperties.getDefaultConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            return fallback;
        }
        String normalized = conversationId.trim();
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        return normalized;
    }
}
