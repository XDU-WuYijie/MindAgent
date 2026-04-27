package com.mindagent.agent.service;

import com.mindagent.agent.entity.KnowledgeChunk;
import com.mindagent.agent.entity.KnowledgeDocument;
import com.mindagent.agent.repository.KnowledgeChunkRepository;
import com.mindagent.agent.repository.KnowledgeDocumentRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Bm25RetrievalService {

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\p{IsHan}]");
    private static final double K1 = 1.5d;
    private static final double B = 0.75d;

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private volatile List<IndexedChunk> indexedChunks = List.of();
    private volatile double averageLength = 1d;

    public Bm25RetrievalService(KnowledgeDocumentRepository knowledgeDocumentRepository,
                                KnowledgeChunkRepository knowledgeChunkRepository) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    public synchronized void refresh() {
        List<KnowledgeDocument> documents = knowledgeDocumentRepository.findAllByStatusOrderByCreatedAtDesc("ACTIVE");
        if (documents.isEmpty()) {
            indexedChunks = List.of();
            averageLength = 1d;
            return;
        }
        Map<Long, KnowledgeDocument> documentsById = new LinkedHashMap<>();
        for (KnowledgeDocument document : documents) {
            documentsById.put(document.getId(), document);
        }
        List<KnowledgeChunk> chunks = knowledgeChunkRepository.findAllByDocumentIdInOrderByDocumentIdAscChunkIndexAsc(documentsById.keySet());
        List<IndexedChunk> loaded = new ArrayList<>();
        int totalLength = 0;
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            KnowledgeDocument document = documentsById.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            IndexedChunk indexed = IndexedChunk.from(document, chunk, tokenizeForCount(chunk.getContent()));
            loaded.add(indexed);
            totalLength += indexed.length();
            for (String token : indexed.termFrequency().keySet()) {
                documentFrequency.merge(token, 1, Integer::sum);
            }
        }
        double avgLength = loaded.isEmpty() ? 1d : (double) totalLength / loaded.size();
        List<IndexedChunk> normalized = new ArrayList<>(loaded.size());
        for (IndexedChunk chunk : loaded) {
            normalized.add(chunk.withDocumentFrequency(documentFrequency));
        }
        indexedChunks = normalized;
        averageLength = avgLength <= 0 ? 1d : avgLength;
    }

    public List<RetrievedChunk> search(QueryRewriteResult rewriteResult, RetrievalFilter filter, int topK) {
        if (indexedChunks.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> queryTerms = tokenizeForCount(rewriteResult.rewrittenQuery());
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        int corpusSize = indexedChunks.size();
        List<RetrievedChunk> ranked = new ArrayList<>();
        for (IndexedChunk chunk : indexedChunks) {
            if (!matchesFilter(chunk, filter)) {
                continue;
            }
            double score = score(queryTerms, chunk, corpusSize);
            if (score <= 0) {
                continue;
            }
            ranked.add(chunk.toRetrievedChunk().withBm25(score, 0));
        }
        ranked.sort(Comparator.comparingDouble(RetrievedChunk::bm25Score).reversed());
        int limit = Math.max(1, topK);
        List<RetrievedChunk> output = new ArrayList<>();
        for (int i = 0; i < ranked.size() && i < limit; i++) {
            RetrievedChunk chunk = ranked.get(i);
            output.add(chunk.withBm25(chunk.bm25Score(), i + 1));
        }
        return output;
    }

    private double score(Map<String, Integer> queryTerms, IndexedChunk chunk, int corpusSize) {
        double score = 0d;
        for (Map.Entry<String, Integer> entry : queryTerms.entrySet()) {
            String term = entry.getKey();
            Integer freq = chunk.termFrequency().get(term);
            if (freq == null || freq <= 0) {
                continue;
            }
            int df = chunk.documentFrequency().getOrDefault(term, 0);
            double idf = Math.log(1d + ((corpusSize - df + 0.5d) / (df + 0.5d)));
            double numerator = freq * (K1 + 1d);
            double denominator = freq + K1 * (1d - B + B * chunk.length() / averageLength);
            score += idf * (numerator / denominator);
        }
        return score;
    }

    private boolean matchesFilter(IndexedChunk chunk, RetrievalFilter filter) {
        if (filter == null) {
            return true;
        }
        return matchValues(chunk.knowledgeBaseKey(), filter.knowledgeBaseKeys())
                && matchValues(chunk.category(), filter.categories())
                && matchValues(chunk.sourceType(), filter.sourceTypes());
    }

    private boolean matchValues(String actual, Set<String> expected) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (actual == null || actual.isBlank()) {
            return false;
        }
        for (String value : expected) {
            if (actual.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> tokenizeForCount(String text) {
        Map<String, Integer> terms = new LinkedHashMap<>();
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Matcher wordMatcher = WORD_PATTERN.matcher(normalized);
        while (wordMatcher.find()) {
            terms.merge(wordMatcher.group(), 1, Integer::sum);
        }
        List<String> cjk = new ArrayList<>();
        Matcher cjkMatcher = CJK_PATTERN.matcher(normalized);
        while (cjkMatcher.find()) {
            cjk.add(cjkMatcher.group());
        }
        for (int i = 0; i < cjk.size(); i++) {
            terms.merge(cjk.get(i), 1, Integer::sum);
            if (i + 1 < cjk.size()) {
                terms.merge(cjk.get(i) + cjk.get(i + 1), 1, Integer::sum);
            }
        }
        return terms;
    }

    private record IndexedChunk(
            String chunkId,
            Long documentId,
            String knowledgeBaseKey,
            String docName,
            String sourceType,
            String category,
            int tokenCount,
            String content,
            Map<String, Integer> termFrequency,
            int length,
            Map<String, Integer> documentFrequency
    ) {

        static IndexedChunk from(KnowledgeDocument document, KnowledgeChunk chunk, Map<String, Integer> tf) {
            int length = tf.values().stream().mapToInt(Integer::intValue).sum();
            return new IndexedChunk(
                    chunk.getChunkId(),
                    document.getId(),
                    document.getKnowledgeBaseKey(),
                    document.getDocName(),
                    document.getSourceType(),
                    chunk.getCategory(),
                    chunk.getTokenCount() == null ? 0 : chunk.getTokenCount(),
                    chunk.getContent(),
                    tf,
                    Math.max(1, length),
                    Map.of()
            );
        }

        IndexedChunk withDocumentFrequency(Map<String, Integer> df) {
            return new IndexedChunk(chunkId, documentId, knowledgeBaseKey, docName, sourceType, category, tokenCount, content,
                    termFrequency, length, Map.copyOf(df));
        }

        RetrievedChunk toRetrievedChunk() {
            return new RetrievedChunk(chunkId, documentId, knowledgeBaseKey, docName, sourceType, category, tokenCount, content,
                    0d, 0, 0d, 0, 0d, 0d, 0);
        }
    }
}
