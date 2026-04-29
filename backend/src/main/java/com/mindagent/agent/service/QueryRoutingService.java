package com.mindagent.agent.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class QueryRoutingService {

    private static final String QUERY_TYPE_PROMPT = """
            You are a query router for a campus mental-health assistant.
            Output only one label:
            - APPOINTMENT_ACTION: the user wants the system to actually query available slots, create an appointment, list my appointments, or cancel my appointment.
            - APPOINTMENT_PROCESS: booking counseling, cancellation, reschedule, status, notification, appointment records or campus internal process.
            - PSYCHOLOGY_KNOWLEDGE: anxiety, stress, sleep, emotions, relationships, self-help psychoeducation.
            - OTHER: greeting, small talk, unrelated chat, or any question that is neither about mental health knowledge nor appointment process.
            If a query asks the assistant to perform an appointment operation, output APPOINTMENT_ACTION.
            If a query mixes psychological distress and booking process explanation, output APPOINTMENT_PROCESS.
            Output exactly one token.
            """;

    private final ChatModelGateway chatModelGateway;

    public QueryRoutingService(ChatModelGateway chatModelGateway) {
        this.chatModelGateway = chatModelGateway;
    }

    public Mono<QueryType> classify(String query, String requestedModel) {
        QueryType byRule = classifyByRule(query);
        if (byRule != null) {
            return Mono.just(byRule);
        }
        List<Map<String, Object>> messages = List.of(
                ChatMessageFactory.message("system", QUERY_TYPE_PROMPT),
                ChatMessageFactory.message("user", query == null ? "" : query)
        );
        return chatModelGateway.completeOnce(messages, requestedModel)
                .map(this::mapLabel)
                .defaultIfEmpty(QueryType.PSYCHOLOGY_KNOWLEDGE);
    }

    QueryType classifyByRule(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return QueryType.OTHER;
        }
        boolean appointmentAction = containsAny(normalized,
                "帮我预约", "我想预约", "替我预约", "给我预约", "取消我的预约", "帮我取消", "查我的预约", "看看还有哪些时间", "查询可用时间");
        boolean appointment = containsAny(normalized,
                "预约", "咨询预约", "预约咨询", "取消预约", "改期", "预约状态", "我的预约", "预约记录", "通知", "提醒", "时间段", "申请");
        boolean psych = containsAny(normalized,
                "焦虑", "抑郁", "难过", "低落", "压力", "睡不着", "失眠", "情绪", "人际", "同学", "室友", "考试", "崩溃", "烦");
        boolean greeting = containsAny(normalized,
                "你好", "hi", "hello", "在吗", "早上好", "晚上好", "谢谢", "哈哈");
        if (appointmentAction) {
            return QueryType.APPOINTMENT_ACTION;
        }
        if (appointment) {
            return QueryType.APPOINTMENT_PROCESS;
        }
        if (psych) {
            return QueryType.PSYCHOLOGY_KNOWLEDGE;
        }
        if (greeting && normalized.length() <= 12) {
            return QueryType.OTHER;
        }
        return null;
    }

    private QueryType mapLabel(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("APPOINTMENT_ACTION")) {
            return QueryType.APPOINTMENT_ACTION;
        }
        if (normalized.contains("APPOINTMENT")) {
            return QueryType.APPOINTMENT_PROCESS;
        }
        if (normalized.contains("OTHER")) {
            return QueryType.OTHER;
        }
        return QueryType.PSYCHOLOGY_KNOWLEDGE;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
