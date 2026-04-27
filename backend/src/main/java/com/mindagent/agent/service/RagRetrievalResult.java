package com.mindagent.agent.service;

import java.util.List;

public record RagRetrievalResult(
        IntentType intentType,
        QueryType queryType,
        String rewrittenQuery,
        List<RetrievedChunk> bm25Results,
        List<RetrievedChunk> vectorResults,
        List<RetrievedChunk> fusedResults,
        List<RetrievedChunk> rerankResults,
        List<RetrievedChunk> selectedResults
) {
}
