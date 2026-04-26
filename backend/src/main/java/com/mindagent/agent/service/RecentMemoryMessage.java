package com.mindagent.agent.service;

import java.time.LocalDateTime;

public record RecentMemoryMessage(
        Long messageId,
        String role,
        String content,
        Integer tokenCount,
        LocalDateTime createdAt
) {
}
