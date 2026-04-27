package com.mindagent.agent.service;

import com.mindagent.agent.config.RagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RetrievalPostProcessor {

    private final RagProperties ragProperties;

    public RetrievalPostProcessor(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<RetrievedChunk> select(QueryType queryType, List<RetrievedChunk> fusedResults) {
        List<RetrievedChunk> selected = new ArrayList<>();
        int usedTokens = 0;
        for (RetrievedChunk chunk : fusedResults) {
            if (chunk.fusedScore() <= 0) {
                continue;
            }
            if (countDocChunks(selected, chunk.documentId()) >= Math.max(1, ragProperties.getMaxChunksPerDoc())) {
                continue;
            }
            if (queryType == QueryType.APPOINTMENT_PROCESS
                    && "faq".equalsIgnoreCase(chunk.sourceType())
                    && chunk.category() != null
                    && chunk.category().toUpperCase(Locale.ROOT).contains("APPOINTMENT")) {
                chunk = chunk.withFusedScore(chunk.fusedScore() + 0.01d);
            }
            int nextTokens = usedTokens + Math.max(0, chunk.tokenCount());
            if (!selected.isEmpty() && nextTokens > ragProperties.getMaxContextTokens()) {
                continue;
            }
            selected.add(chunk);
            usedTokens = nextTokens;
            if (selected.size() >= Math.max(1, ragProperties.getFinalTopK())) {
                break;
            }
        }
        return selected;
    }

    private int countDocChunks(List<RetrievedChunk> selected, Long documentId) {
        if (documentId == null) {
            return 0;
        }
        int count = 0;
        for (RetrievedChunk item : selected) {
            if (documentId.equals(item.documentId())) {
                count++;
            }
        }
        return count;
    }
}
