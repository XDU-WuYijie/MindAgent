package com.mindagent.agent.service;

public record ToolExecutionView(
        String toolName,
        String arguments,
        String result,
        String status
) {
}
