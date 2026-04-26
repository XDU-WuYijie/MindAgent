package com.mindagent.agent.controller;

import com.mindagent.agent.entity.ChatMessage;
import com.mindagent.agent.entity.PsychologicalReport;
import com.mindagent.agent.repository.ChatMessageRepository;
import com.mindagent.agent.repository.PsychologicalReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final ChatMessageRepository chatMessageRepository;
    private final PsychologicalReportRepository reportRepository;

    public MeController(ChatMessageRepository chatMessageRepository,
                        PsychologicalReportRepository reportRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.reportRepository = reportRepository;
    }

    @GetMapping("/messages")
    public List<ChatMessage> messages(@AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt);
        return chatMessageRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    @GetMapping("/reports")
    public Map<String, Object> reports(@AuthenticationPrincipal Jwt jwt) {
        Long userId = resolveUserId(jwt);
        List<PsychologicalReport> rows = reportRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        return Map.of("count", rows.size(), "items", rows);
    }

    private Long resolveUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        }

        Object claim = jwt.getClaim("uid");
        if (claim instanceof Number number) {
            return number.longValue();
        }
        if (claim instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token uid");
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token uid");
    }
}
