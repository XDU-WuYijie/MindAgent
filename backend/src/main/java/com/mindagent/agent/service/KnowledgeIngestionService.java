package com.mindagent.agent.service;

import com.mindagent.agent.entity.KnowledgeDocument;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class KnowledgeIngestionService {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeIngestionService(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Async("knowledgeTaskExecutor")
    public CompletableFuture<Void> processDocumentAsync(Long documentId) {
        knowledgeBaseService.processDocument(documentId, true);
        return CompletableFuture.completedFuture(null);
    }

    @Async("knowledgeTaskExecutor")
    public CompletableFuture<Void> processDocumentAsync(Long documentId, boolean refreshCache) {
        knowledgeBaseService.processDocument(documentId, refreshCache);
        return CompletableFuture.completedFuture(null);
    }

    public BatchIngestResult ingestExistingFiles(List<Path> files) throws Exception {
        int importedFiles = 0;
        long importedBytes = 0L;
        int chunks = 0;
        for (Path file : files) {
            KnowledgeDocument document = knowledgeBaseService.registerExistingKnowledgeDocument(file);
            int processedChunks = knowledgeBaseService.processDocument(document.getId(), false);
            importedFiles++;
            importedBytes += document.getFileBytes() == null ? 0L : document.getFileBytes();
            chunks += processedChunks;
        }
        knowledgeBaseService.reload();
        return new BatchIngestResult(importedFiles, importedBytes, chunks);
    }

    public record BatchIngestResult(int importedFiles, long importedBytes, int chunks) {
    }
}
