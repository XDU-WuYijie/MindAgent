package com.mindagent.agent.service;

import java.util.List;

public record ToolChatResult(
        String answer,
        List<ToolExecutionView> toolExecutions
) {
}
