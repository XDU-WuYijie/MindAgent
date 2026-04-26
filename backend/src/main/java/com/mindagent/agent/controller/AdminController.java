package com.mindagent.agent.controller;

import com.mindagent.agent.entity.ChatMessage;
import com.mindagent.agent.entity.McpDispatchLog;
import com.mindagent.agent.entity.PsychologicalReport;
import com.mindagent.agent.repository.ChatMessageRepository;
import com.mindagent.agent.repository.McpDispatchLogRepository;
import com.mindagent.agent.repository.PsychologicalReportRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ChatMessageRepository chatMessageRepository;
    private final PsychologicalReportRepository reportRepository;
    private final McpDispatchLogRepository mcpDispatchLogRepository;

    public AdminController(ChatMessageRepository chatMessageRepository,
                           PsychologicalReportRepository reportRepository,
                           McpDispatchLogRepository mcpDispatchLogRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.reportRepository = reportRepository;
        this.mcpDispatchLogRepository = mcpDispatchLogRepository;
    }

    @GetMapping("/messages")
    public List<ChatMessage> messages(@RequestParam(defaultValue = "1") Long userId) {
        return chatMessageRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    @GetMapping("/reports")
    public Map<String, Object> reports() {
        List<PsychologicalReport> rows = reportRepository.findAll();
        return Map.of("count", rows.size(), "items", rows);
    }

    @GetMapping("/mcp-logs")
    public List<McpDispatchLog> mcpLogs() {
        return mcpDispatchLogRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
