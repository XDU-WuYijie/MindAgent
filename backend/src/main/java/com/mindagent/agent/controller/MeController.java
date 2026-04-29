package com.mindagent.agent.controller;

import com.mindagent.agent.entity.ChatMessage;
import com.mindagent.agent.entity.PsychologicalReport;
import com.mindagent.agent.repository.ChatMessageRepository;
import com.mindagent.agent.repository.PsychologicalReportRepository;
import com.mindagent.agent.service.CurrentUserSupport;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final ChatMessageRepository chatMessageRepository;
    private final PsychologicalReportRepository reportRepository;
    private final CurrentUserSupport currentUserSupport;

    public MeController(ChatMessageRepository chatMessageRepository,
                        PsychologicalReportRepository reportRepository,
                        CurrentUserSupport currentUserSupport) {
        this.chatMessageRepository = chatMessageRepository;
        this.reportRepository = reportRepository;
        this.currentUserSupport = currentUserSupport;
    }

    @GetMapping("/messages")
    public List<ChatMessage> messages(@AuthenticationPrincipal Jwt jwt) {
        Long userId = currentUserSupport.requireUserId(jwt);
        return chatMessageRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    @GetMapping("/reports")
    public Map<String, Object> reports(@AuthenticationPrincipal Jwt jwt) {
        Long userId = currentUserSupport.requireUserId(jwt);
        List<PsychologicalReport> rows = reportRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        return Map.of("count", rows.size(), "items", rows);
    }
}
