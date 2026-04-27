package com.mindagent.agent.service;

import com.mindagent.agent.config.RagProperties;
import com.mindagent.agent.config.RerankProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class RagRetrievalService {

    private final RagProperties ragProperties;
    private final QueryRewriteService queryRewriteService;
    private final Bm25RetrievalService bm25RetrievalService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RrfFusionService rrfFusionService;
    private final QwenRerankService qwenRerankService;
    private final RetrievalPostProcessor retrievalPostProcessor;
    private final RerankProperties rerankProperties;

    public RagRetrievalService(RagProperties ragProperties,
                               QueryRewriteService queryRewriteService,
                               Bm25RetrievalService bm25RetrievalService,
                               KnowledgeBaseService knowledgeBaseService,
                               RrfFusionService rrfFusionService,
                               QwenRerankService qwenRerankService,
                               RetrievalPostProcessor retrievalPostProcessor,
                               RerankProperties rerankProperties) {
        this.ragProperties = ragProperties;
        this.queryRewriteService = queryRewriteService;
        this.bm25RetrievalService = bm25RetrievalService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.rrfFusionService = rrfFusionService;
        this.qwenRerankService = qwenRerankService;
        this.retrievalPostProcessor = retrievalPostProcessor;
        this.rerankProperties = rerankProperties;
    }

    public reactor.core.publisher.Mono<RagRetrievalResult> retrieve(IntentType intentType, QueryType queryType, String query) {
        if (!ragProperties.isEnabled() || queryType == QueryType.OTHER) {
            return reactor.core.publisher.Mono.just(
                    new RagRetrievalResult(intentType, queryType, query == null ? "" : query, List.of(), List.of(), List.of(), List.of(), List.of())
            );
        }
        QueryRewriteResult rewritten = queryRewriteService.rewrite(query, queryType);
        RetrievalFilter filter = filterFor(queryType);
        List<RetrievedChunk> bm25Results = bm25RetrievalService.search(rewritten, filter, ragProperties.getBm25TopK());
        List<RetrievedChunk> vectorResults = knowledgeBaseService.retrieveVectorChunks(rewritten.rewrittenQuery(), filter, ragProperties.getVectorTopK());
        List<RetrievedChunk> fusedResults = rrfFusionService.fuse(queryType, bm25Results, vectorResults);
        List<RetrievedChunk> fusedCandidates = fusedResults.stream()
                .limit(Math.max(1, rerankProperties.getCandidateTopK()))
                .toList();
        return qwenRerankService.rerank(rewritten.rewrittenQuery(), fusedCandidates)
                .map(rerankResults -> {
                    List<RetrievedChunk> selectedResults = retrievalPostProcessor.select(queryType, rerankResults);
                    return new RagRetrievalResult(intentType, queryType, rewritten.rewrittenQuery(), bm25Results, vectorResults, fusedCandidates, rerankResults, selectedResults);
                });
    }

    private RetrievalFilter filterFor(QueryType queryType) {
        return switch (queryType) {
            case APPOINTMENT_PROCESS -> new RetrievalFilter(
                    Set.of("knowledge_base_3"),
                    Set.of("APPOINTMENT_PROCESS"),
                    Set.of("faq", "kb", "article", "generic")
            );
            case PSYCHOLOGY_KNOWLEDGE -> new RetrievalFilter(
                    Set.of("knowledge_base_1"),
                    Set.of(),
                    Set.of("faq", "kb", "article", "generic")
            );
            case OTHER -> RetrievalFilter.empty();
        };
    }
}
