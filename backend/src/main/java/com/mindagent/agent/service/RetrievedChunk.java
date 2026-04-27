package com.mindagent.agent.service;

public record RetrievedChunk(
        String chunkId,
        Long documentId,
        String knowledgeBaseKey,
        String docName,
        String sourceType,
        String category,
        int tokenCount,
        String content,
        double bm25Score,
        int bm25Rank,
        double vectorScore,
        int vectorRank,
        double fusedScore,
        double rerankScore,
        int rerankRank
) {

    public RetrievedChunk withBm25(double score, int rank) {
        return new RetrievedChunk(chunkId, documentId, knowledgeBaseKey, docName, sourceType, category, tokenCount, content,
                score, rank, vectorScore, vectorRank, fusedScore, rerankScore, rerankRank);
    }

    public RetrievedChunk withVector(double score, int rank) {
        return new RetrievedChunk(chunkId, documentId, knowledgeBaseKey, docName, sourceType, category, tokenCount, content,
                bm25Score, bm25Rank, score, rank, fusedScore, rerankScore, rerankRank);
    }

    public RetrievedChunk withFusedScore(double score) {
        return new RetrievedChunk(chunkId, documentId, knowledgeBaseKey, docName, sourceType, category, tokenCount, content,
                bm25Score, bm25Rank, vectorScore, vectorRank, score, rerankScore, rerankRank);
    }

    public RetrievedChunk withRerank(double score, int rank) {
        return new RetrievedChunk(chunkId, documentId, knowledgeBaseKey, docName, sourceType, category, tokenCount, content,
                bm25Score, bm25Rank, vectorScore, vectorRank, fusedScore, score, rank);
    }
}
