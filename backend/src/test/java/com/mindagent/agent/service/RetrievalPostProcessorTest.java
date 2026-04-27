package com.mindagent.agent.service;

import com.mindagent.agent.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrievalPostProcessorTest {

    @Test
    void shouldLimitChunksPerDocumentAndTotalResults() {
        RagProperties properties = new RagProperties();
        properties.setFinalTopK(2);
        properties.setMaxChunksPerDoc(1);
        properties.setMaxContextTokens(1000);
        RetrievalPostProcessor processor = new RetrievalPostProcessor(properties);

        List<RetrievedChunk> selected = processor.select(QueryType.APPOINTMENT_PROCESS, List.of(
                new RetrievedChunk("c1", 1L, "knowledge_base_3", "doc1", "faq", "APPOINTMENT_PROCESS", 120, "A", 0, 1, 0, 1, 0.5, 0.9, 1),
                new RetrievedChunk("c2", 1L, "knowledge_base_3", "doc1", "faq", "APPOINTMENT_PROCESS", 120, "B", 0, 2, 0, 2, 0.4, 0.8, 2),
                new RetrievedChunk("c3", 2L, "knowledge_base_3", "doc2", "kb", "APPOINTMENT_PROCESS", 120, "C", 0, 3, 0, 3, 0.3, 0.7, 3)
        ));

        assertEquals(2, selected.size());
        assertEquals(List.of("c1", "c3"), selected.stream().map(RetrievedChunk::chunkId).toList());
    }
}
