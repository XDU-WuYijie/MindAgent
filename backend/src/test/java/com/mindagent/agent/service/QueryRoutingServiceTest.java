package com.mindagent.agent.service;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueryRoutingServiceTest {

    // ========== 规则命中测试（直接关键词匹配） ==========

    @Test
    void shouldRouteMixedQuestionToAppointmentFirst() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        QueryType queryType = service.classify("我最近很焦虑，想预约咨询怎么办", null).block();
        assertEquals(QueryType.APPOINTMENT_PROCESS, queryType);
    }

    @Test
    void shouldRouteAppointmentActionByRule() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        QueryType queryType = service.classify("帮我预约一个心理咨询时间", null).block();
        assertEquals(QueryType.APPOINTMENT_ACTION, queryType);
    }

    @Test
    void shouldRouteAvailableTeacherQueryToAppointmentActionByRule() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        QueryType queryType = service.classify("现在有哪个老师可以预约？请查询真实可预约时段。", null).block();
        assertEquals(QueryType.APPOINTMENT_ACTION, queryType);
    }

    @Test
    void shouldRoutePsychologyQuestionByRule() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        QueryType queryType = service.classify("考试前特别焦虑怎么办", null).block();
        assertEquals(QueryType.PSYCHOLOGY_KNOWLEDGE, queryType);
    }

    @Test
    void shouldRouteEmptyQueryToOther() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        assertEquals(QueryType.OTHER, service.classifyByRule(""));
        assertEquals(QueryType.OTHER, service.classifyByRule("   "));
        assertEquals(QueryType.OTHER, service.classifyByRule(null));
    }

    @Test
    void shouldRouteGreetingToOther() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        assertEquals(QueryType.OTHER, service.classifyByRule("你好"));
        assertEquals(QueryType.OTHER, service.classifyByRule("hi"));
        assertEquals(QueryType.OTHER, service.classifyByRule("在吗"));
    }

    @Test
    void shouldRouteLongGreetingToNull() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        assertNull(service.classifyByRule("你好啊，今天天气真不错，我想问问学校的事情"));
    }

    // ========== LLM 动态路由测试（规则未命中时走模型分类） ==========

    @Test
    void shouldRouteViaModelToAppointmentProcess() {
        // 不直接命中关键词，LLM 判断为预约流程
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("APPOINTMENT_PROCESS"));
        QueryType queryType = service.classify("请问怎么在系统里取消已经定好的咨询", null).block();
        assertEquals(QueryType.APPOINTMENT_PROCESS, queryType);
    }

    @Test
    void shouldRouteViaModelToAppointmentAction() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("APPOINTMENT_ACTION"));
        QueryType queryType = service.classify("请直接替我处理一下咨询安排", null).block();
        assertEquals(QueryType.APPOINTMENT_ACTION, queryType);
    }

    @Test
    void shouldRouteViaModelToPsychologyKnowledge() {
        // 不直接命中关键词，LLM 判断为心理健康知识
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("PSYCHOLOGY_KNOWLEDGE"));
        QueryType queryType = service.classify("总是感觉心里堵得慌，静不下来", null).block();
        assertEquals(QueryType.PSYCHOLOGY_KNOWLEDGE, queryType);
    }

    @Test
    void shouldRouteViaModelToOther() {
        // 不直接命中关键词，LLM 判断为兜底 OTHER
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        QueryType queryType = service.classify("今天天气怎么样", null).block();
        assertEquals(QueryType.OTHER, queryType);
    }

    @Test
    void shouldDefaultToPsychologyKnowledgeWhenModelReturnsEmpty() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway(""));
        QueryType queryType = service.classify("不知道说什么", null).block();
        assertEquals(QueryType.PSYCHOLOGY_KNOWLEDGE, queryType);
    }

    // ========== 边界测试 ==========

    @Test
    void shouldRouteAppointmentByRuleWhenContainsKeyword() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        assertEquals(QueryType.APPOINTMENT_PROCESS, service.classifyByRule("我想改期"));
        assertEquals(QueryType.APPOINTMENT_PROCESS, service.classifyByRule("查看预约状态"));
        assertEquals(QueryType.APPOINTMENT_PROCESS, service.classifyByRule("预约记录在哪看"));
    }

    @Test
    void shouldRoutePsychologyByRuleWhenContainsKeyword() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        assertEquals(QueryType.PSYCHOLOGY_KNOWLEDGE, service.classifyByRule("最近压力很大"));
        assertEquals(QueryType.PSYCHOLOGY_KNOWLEDGE, service.classifyByRule("睡不着怎么办"));
        assertEquals(QueryType.PSYCHOLOGY_KNOWLEDGE, service.classifyByRule("和室友关系不好"));
    }

    @Test
    void shouldNotRouteGreetingLongerThan12Chars() {
        QueryRoutingService service = new QueryRoutingService(new StubChatGateway("OTHER"));
        // 超过 12 个字符的问候语不应被规则直接路由，应返回 null 交给 LLM 判断
        String longGreeting = "你好你好你好你好你好你好你好";
        assertNull(service.classifyByRule(longGreeting));
    }

    private record StubChatGateway(String reply) implements ChatModelGateway {
        @Override
        public reactor.core.publisher.Flux<String> streamChat(List<Map<String, Object>> messages, String requestedModel) {
            return reactor.core.publisher.Flux.empty();
        }

        @Override
        public Mono<String> completeOnce(List<Map<String, Object>> messages, String requestedModel) {
            return Mono.just(reply);
        }
    }
}
