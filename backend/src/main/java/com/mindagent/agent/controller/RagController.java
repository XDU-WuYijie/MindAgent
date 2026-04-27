package com.mindagent.agent.controller;

import com.mindagent.agent.dto.RagDebugRequest;
import com.mindagent.agent.service.ChatOrchestrationService;
import com.mindagent.agent.service.IntentType;
import com.mindagent.agent.service.QueryRoutingService;
import com.mindagent.agent.service.QueryType;
import com.mindagent.agent.service.RagRetrievalResult;
import com.mindagent.agent.service.RagRetrievalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final ChatOrchestrationService chatOrchestrationService;
    private final QueryRoutingService queryRoutingService;
    private final RagRetrievalService ragRetrievalService;

    public RagController(ChatOrchestrationService chatOrchestrationService,
                         QueryRoutingService queryRoutingService,
                         RagRetrievalService ragRetrievalService) {
        this.chatOrchestrationService = chatOrchestrationService;
        this.queryRoutingService = queryRoutingService;
        this.ragRetrievalService = ragRetrievalService;
    }

    @PostMapping("/retrieve/debug")
    public Mono<Map<String, Object>> debugRetrieve(@Valid @RequestBody RagDebugRequest request) {
        return chatOrchestrationService.classifyIntent(request.getQuery(), request.getModel())
                .flatMap(intentType -> queryRoutingService.classify(request.getQuery(), request.getModel())
                        .defaultIfEmpty(QueryType.OTHER)
                        .flatMap(queryType -> ragRetrievalService.retrieve(intentType, queryType, request.getQuery())
                                .map(result -> toPayload(intentType, queryType, result))));
    }

    private Map<String, Object> toPayload(IntentType intentType, QueryType queryType, RagRetrievalResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("intentType", intentType.name());
        payload.put("queryType", queryType.name());
        payload.put("rewrittenQuery", result.rewrittenQuery());
        payload.put("bm25Results", result.bm25Results());
        payload.put("vectorResults", result.vectorResults());
        payload.put("fusedResults", result.fusedResults());
        payload.put("rerankResults", result.rerankResults());
        payload.put("selectedResults", result.selectedResults());
        return payload;
    }
}
