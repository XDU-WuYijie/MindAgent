package com.mindagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindagent.agent.config.RerankProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QwenRerankService {

    private final WebClient.Builder webClientBuilder;
    private final RerankProperties rerankProperties;
    private final ObjectMapper objectMapper;

    public QwenRerankService(WebClient.Builder webClientBuilder,
                             RerankProperties rerankProperties,
                             ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.rerankProperties = rerankProperties;
        this.objectMapper = objectMapper;
    }

    public Mono<List<RetrievedChunk>> rerank(String query, List<RetrievedChunk> candidates) {
        if (!rerankProperties.isEnabled() || candidates.isEmpty()) {
            return Mono.just(limitAndRank(candidates, rerankProperties.getTopN()));
        }
        String apiKey = rerankProperties.getApiKey() == null ? "" : rerankProperties.getApiKey().trim();
        if (apiKey.isBlank()) {
            return Mono.just(limitAndRank(candidates, rerankProperties.getTopN()));
        }

        List<RetrievedChunk> limited = candidates.stream()
                .limit(Math.max(1, rerankProperties.getCandidateTopK()))
                .toList();
        List<String> documents = limited.stream()
                .map(RetrievedChunk::content)
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", rerankProperties.getModel());
        payload.put("query", query == null ? "" : query);
        payload.put("documents", documents);
        payload.put("top_n", Math.min(Math.max(1, rerankProperties.getTopN()), limited.size()));
        payload.put("instruct", rerankProperties.getInstruct());

        return webClientBuilder.build()
                .post()
                .uri(rerankProperties.getBaseUrl() + rerankProperties.getPath())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(apiKey))
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> mapRerankResults(body, limited))
                .onErrorResume(ex -> Mono.just(limitAndRank(limited, rerankProperties.getTopN())));
    }

    private List<RetrievedChunk> mapRerankResults(String body, List<RetrievedChunk> candidates) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return limitAndRank(candidates, rerankProperties.getTopN());
            }
            List<RetrievedChunk> reranked = new ArrayList<>();
            int rank = 1;
            for (JsonNode node : results) {
                int index = node.path("index").asInt(-1);
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                double score = node.path("relevance_score").asDouble(0d);
                reranked.add(candidates.get(index).withRerank(score, rank++));
            }
            if (reranked.isEmpty()) {
                return limitAndRank(candidates, rerankProperties.getTopN());
            }
            return reranked;
        } catch (Exception ignored) {
            return limitAndRank(candidates, rerankProperties.getTopN());
        }
    }

    private List<RetrievedChunk> limitAndRank(List<RetrievedChunk> candidates, int topN) {
        List<RetrievedChunk> ranked = new ArrayList<>();
        int limit = Math.min(Math.max(1, topN), candidates.size());
        for (int i = 0; i < limit; i++) {
            RetrievedChunk chunk = candidates.get(i);
            double score = chunk.fusedScore() > 0 ? chunk.fusedScore() : (limit - i);
            ranked.add(chunk.withRerank(score, i + 1));
        }
        return ranked;
    }
}
