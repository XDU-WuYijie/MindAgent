package com.mindagent.agent.controller;

import com.mindagent.agent.entity.KnowledgeChunk;
import com.mindagent.agent.entity.KnowledgeDocument;
import com.mindagent.agent.service.KnowledgeBaseService;
import com.mindagent.agent.service.KnowledgeIngestionService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeIngestionService knowledgeIngestionService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeIngestionService knowledgeIngestionService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeIngestionService = knowledgeIngestionService;
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        knowledgeBaseService.reload();
        return Map.of(
                "ok", true,
                "chunks", knowledgeBaseService.size()
        );
    }

    @GetMapping("/documents")
    public Map<String, Object> documents() {
        List<Map<String, Object>> items = knowledgeBaseService.listKnowledgeDocuments().stream()
                .map(this::documentSummary)
                .collect(Collectors.toList());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("count", items.size());
        response.put("items", items);
        return response;
    }

    @GetMapping("/documents/{id}")
    public Map<String, Object> document(@PathVariable("id") Long id) {
        KnowledgeDocument document = knowledgeBaseService.getKnowledgeDocument(id);
        List<KnowledgeChunk> chunks = knowledgeBaseService.listKnowledgeChunks(id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("document", documentDetail(document));
        response.put("vectorStoreWritten", isVectorStored(document));
        response.put("chunks", chunks.stream().map(this::chunkDetail).collect(Collectors.toList()));
        return response;
    }

    @DeleteMapping("/documents/{id}")
    public Map<String, Object> deleteDocument(@PathVariable("id") Long id) {
        KnowledgeDocument document = knowledgeBaseService.deleteKnowledgeDocument(id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("document", documentDetail(document));
        response.put("vectorStoreWritten", false);
        return response;
    }

    @GetMapping("/spaces")
    public Map<String, Object> spaces() {
        List<Map<String, Object>> items = knowledgeBaseService.listKnowledgeSpaces().stream()
                .map(space -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", space.key());
                    item.put("label", space.title());
                    item.put("description", space.description());
                    item.put("uploadLabel", knowledgeBaseService.knowledgeBaseLabel(space.key()));
                    return item;
                })
                .collect(Collectors.toList());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("count", items.size());
        response.put("items", items);
        return response;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> upload(@RequestPart("file") FilePart filePart,
                                            @RequestParam(value = "kind", required = false) String kind,
                                            @RequestParam(value = "space", required = false) String space) {
        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> Mono.fromCallable(() -> {
                    try {
                        byte[] content = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(content);
                        KnowledgeDocument document = knowledgeBaseService.registerKnowledgeDocument(
                                filePart.filename(),
                                content,
                                false,
                                kind,
                                space
                        );
                        knowledgeIngestionService.processDocumentAsync(document.getId());
                        return Map.<String, Object>of(
                                "ok", true,
                                "documentId", document.getId(),
                                "file", document.getStoredFilename(),
                                "status", document.getStatus(),
                                "bytes", document.getFileBytes(),
                                "chunks", document.getChunkCount(),
                                "kind", document.getSourceType(),
                                "space", document.getKnowledgeBaseKey()
                        );
                    } catch (IllegalArgumentException ex) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
                    } catch (Exception ex) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                ex.getMessage() == null ? "upload failed" : ex.getMessage(), ex);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private Map<String, Object> documentSummary(KnowledgeDocument document) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", document.getId());
        summary.put("originalFilename", document.getOriginalFilename());
        summary.put("storedFilename", document.getStoredFilename());
        summary.put("docName", document.getDocName());
        summary.put("sourceType", document.getSourceType());
        summary.put("typeLabel", typeLabel(document.getSourceType()));
        summary.put("knowledgeBaseKey", document.getKnowledgeBaseKey());
        summary.put("knowledgeBaseLabel", knowledgeBaseLabel(document.getKnowledgeBaseKey()));
        summary.put("category", primaryCategory(document.getId()));
        summary.put("status", document.getStatus());
        summary.put("statusLabel", statusLabel(document.getStatus()));
        summary.put("chunkCount", document.getChunkCount());
        summary.put("vectorCount", document.getVectorCount());
        summary.put("updatedAt", document.getUpdatedAt());
        summary.put("createdAt", document.getCreatedAt());
        summary.put("processedAt", document.getProcessedAt());
        summary.put("vectorStoreWritten", isVectorStored(document));
        return summary;
    }

    private Map<String, Object> documentDetail(KnowledgeDocument document) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", document.getId());
        detail.put("originalFilename", document.getOriginalFilename());
        detail.put("storedFilename", document.getStoredFilename());
        detail.put("storagePath", document.getStoragePath());
        detail.put("docName", document.getDocName());
        detail.put("sourceType", document.getSourceType());
        detail.put("typeLabel", typeLabel(document.getSourceType()));
        detail.put("knowledgeBaseKey", document.getKnowledgeBaseKey());
        detail.put("knowledgeBaseLabel", knowledgeBaseLabel(document.getKnowledgeBaseKey()));
        detail.put("category", primaryCategory(document.getId()));
        detail.put("audience", document.getAudience());
        detail.put("version", document.getVersion());
        detail.put("status", document.getStatus());
        detail.put("statusLabel", statusLabel(document.getStatus()));
        detail.put("errorMessage", document.getErrorMessage());
        detail.put("fileBytes", document.getFileBytes());
        detail.put("chunkCount", document.getChunkCount());
        detail.put("vectorCount", document.getVectorCount());
        detail.put("createdAt", document.getCreatedAt());
        detail.put("updatedAt", document.getUpdatedAt());
        detail.put("processedAt", document.getProcessedAt());
        return detail;
    }

    private Map<String, Object> chunkDetail(KnowledgeChunk chunk) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", chunk.getId());
        detail.put("documentId", chunk.getDocumentId());
        detail.put("chunkIndex", chunk.getChunkIndex());
        detail.put("chunkId", chunk.getChunkId());
        detail.put("sectionKey", chunk.getSectionKey());
        detail.put("sectionTitle", chunk.getSectionTitle());
        detail.put("category", chunk.getCategory());
        detail.put("tags", chunk.getTags());
        detail.put("riskLevel", chunk.getRiskLevel());
        detail.put("sourcePageRange", chunk.getSourcePageRange());
        detail.put("questionText", chunk.getQuestionText());
        detail.put("answerText", chunk.getAnswerText());
        detail.put("content", chunk.getContent());
        detail.put("metadataJson", chunk.getMetadataJson());
        detail.put("tokenCount", chunk.getTokenCount());
        detail.put("createdAt", chunk.getCreatedAt());
        detail.put("vectorStoreWritten", true);
        return detail;
    }

    private String primaryCategory(Long documentId) {
        return knowledgeBaseService.listKnowledgeChunks(documentId).stream()
                .map(KnowledgeChunk::getCategory)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse("");
    }

    private boolean isVectorStored(KnowledgeDocument document) {
        return "ACTIVE".equalsIgnoreCase(document.getStatus())
                && document.getVectorCount() != null
                && document.getVectorCount() > 0;
    }

    private String typeLabel(String sourceType) {
        if (sourceType == null) {
            return "知识文档";
        }
        if (sourceType.toLowerCase().contains("faq")) {
            return "FAQ";
        }
        return "知识文档";
    }

    private String knowledgeBaseLabel(String knowledgeBaseKey) {
        if (knowledgeBaseKey == null || knowledgeBaseKey.isBlank()) {
            return "上传校园心理健康知识";
        }
        return switch (knowledgeBaseKey.toLowerCase()) {
            case "knowledge_base_3" -> "上传校园内部知识库";
            default -> "上传校园心理健康知识";
        };
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status.toUpperCase()) {
            case "PROCESSING" -> "解析中";
            case "ACTIVE" -> "已生效";
            case "FAILED" -> "失败";
            case "DELETED" -> "已删除";
            default -> status;
        };
    }
}
