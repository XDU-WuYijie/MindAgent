package com.mindagent.agent.service;

import com.mindagent.agent.config.RagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RrfFusionService {

    private final RagProperties ragProperties;

    public RrfFusionService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<RetrievedChunk> fuse(QueryType queryType, List<RetrievedChunk> bm25Results, List<RetrievedChunk> vectorResults) {
        RagProperties.QueryTypeWeight weight = ragProperties.weightFor(queryType.name());
        double bm25Weight = weight.getBm25Weight();
        double vectorWeight = weight.getVectorWeight();
        int rrfK = Math.max(1, ragProperties.getRrfK());

        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        for (RetrievedChunk chunk : bm25Results) {
            double score = bm25Weight / (rrfK + Math.max(1, chunk.bm25Rank()));
            merged.merge(chunk.chunkId(), chunk.withFusedScore(score), this::merge);
        }
        for (RetrievedChunk chunk : vectorResults) {
            double score = vectorWeight / (rrfK + Math.max(1, chunk.vectorRank()));
            merged.merge(chunk.chunkId(), chunk.withFusedScore(score), this::merge);
        }

        List<RetrievedChunk> fused = new ArrayList<>(merged.values());
        fused.sort(Comparator.comparingDouble(RetrievedChunk::fusedScore).reversed());
        return fused;
    }

    private RetrievedChunk merge(RetrievedChunk left, RetrievedChunk right) {
        RetrievedChunk merged = left;
        if (merged.bm25Rank() <= 0 && right.bm25Rank() > 0) {
            merged = merged.withBm25(right.bm25Score(), right.bm25Rank());
        }
        if (merged.vectorRank() <= 0 && right.vectorRank() > 0) {
            merged = merged.withVector(right.vectorScore(), right.vectorRank());
        }
        return merged.withFusedScore(merged.fusedScore() + right.fusedScore());
    }
}
