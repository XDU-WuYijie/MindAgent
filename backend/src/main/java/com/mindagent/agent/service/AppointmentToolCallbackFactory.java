package com.mindagent.agent.service;

import com.mindagent.agent.entity.AiToolCallLog;
import com.mindagent.agent.repository.AiToolCallLogRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class AppointmentToolCallbackFactory {

    private final AppointmentToolService appointmentToolService;
    private final AiToolCallLogRepository aiToolCallLogRepository;

    public AppointmentToolCallbackFactory(AppointmentToolService appointmentToolService,
                                          AiToolCallLogRepository aiToolCallLogRepository) {
        this.appointmentToolService = appointmentToolService;
        this.aiToolCallLogRepository = aiToolCallLogRepository;
    }

    public List<ToolCallback> createCallbacks(Long userId, Long sessionId, List<ToolExecutionView> toolExecutions) {
        List<ToolCallback> callbacks = new ArrayList<>();
        callbacks.add(callback(userId, sessionId, toolExecutions, "queryAvailableSlots",
                "Use this first when the user asks which teachers can be booked now, who is free today, or which time slots a specific teacher still has.",
                """
                {"type":"object","properties":{"date":{"type":"string","description":"Optional date filter in yyyy-MM-dd"},"teacherName":{"type":"string","description":"Optional teacher name filter"}},"required":[]}
                """,
                arguments -> appointmentToolService.queryAvailableSlots(userId, arguments)));
        callbacks.add(callback(userId, sessionId, toolExecutions, "createAppointment",
                "Create a counseling appointment for the current user using a chosen slotId.",
                """
                {"type":"object","properties":{"slotId":{"type":"integer","description":"Required slot id returned by queryAvailableSlots"},"note":{"type":"string","description":"Optional short note from the student"}},"required":["slotId"]}
                """,
                arguments -> appointmentToolService.createAppointment(userId, arguments)));
        callbacks.add(callback(userId, sessionId, toolExecutions, "queryMyAppointments",
                "List the current user's appointments before answering status or cancellation questions.",
                """
                {"type":"object","properties":{"status":{"type":"string","description":"Optional appointment status filter"}},"required":[]}
                """,
                arguments -> appointmentToolService.queryMyAppointments(userId, arguments)));
        callbacks.add(callback(userId, sessionId, toolExecutions, "cancelAppointment",
                "Cancel one confirmed appointment for the current user using appointmentId.",
                """
                {"type":"object","properties":{"appointmentId":{"type":"integer","description":"Required appointment id returned by queryMyAppointments"},"reason":{"type":"string","description":"Optional cancellation reason"}},"required":["appointmentId"]}
                """,
                arguments -> appointmentToolService.cancelAppointment(userId, arguments)));
        return callbacks;
    }

    private ToolCallback callback(Long userId,
                                  Long sessionId,
                                  List<ToolExecutionView> toolExecutions,
                                  String name,
                                  String description,
                                  String schema,
                                  Function<String, String> executor) {
        ToolDefinition definition = DefaultToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(schema)
                .build();
        ToolMetadata metadata = DefaultToolMetadata.builder().returnDirect(false).build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return metadata;
            }

            @Override
            public String call(String arguments) {
                return call(arguments, null);
            }

            @Override
            public String call(String arguments, ToolContext toolContext) {
                try {
                    String result = executor.apply(arguments == null ? "{}" : arguments);
                    toolExecutions.add(new ToolExecutionView(name, arguments, result, "SUCCESS"));
                    persistLog(userId, sessionId, name, arguments, result, "SUCCESS");
                    return result;
                } catch (RuntimeException ex) {
                    String result = ex.getMessage() == null ? "tool_call_failed" : ex.getMessage();
                    toolExecutions.add(new ToolExecutionView(name, arguments, result, "FAILED"));
                    persistLog(userId, sessionId, name, arguments, result, "FAILED");
                    throw ex;
                }
            }
        };
    }

    private void persistLog(Long userId, Long sessionId, String name, String arguments, String result, String status) {
        AiToolCallLog row = new AiToolCallLog();
        row.setUserId(userId);
        row.setSessionId(sessionId);
        row.setToolName(name);
        row.setToolArguments(arguments);
        row.setToolResult(result);
        row.setStatus(status);
        aiToolCallLogRepository.save(row);
    }
}
