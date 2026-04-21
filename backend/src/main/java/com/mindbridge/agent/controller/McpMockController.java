package com.mindbridge.agent.controller;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/mock/mcp")
public class McpMockController {

    @PostMapping("/excel/write")
    public Map<String, Object> writeExcel(@RequestBody Map<String, Object> payload) {
        return Map.of(
                "ok", true,
                "action", "excel_write",
                "received", payload,
                "at", LocalDateTime.now().toString()
        );
    }

    @PostMapping("/email/send")
    public Map<String, Object> sendEmail(@RequestBody Map<String, Object> payload) {
        return Map.of(
                "ok", true,
                "action", "email_alert",
                "received", payload,
                "at", LocalDateTime.now().toString()
        );
    }
}
