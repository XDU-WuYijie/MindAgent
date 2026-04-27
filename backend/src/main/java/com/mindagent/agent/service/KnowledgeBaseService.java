package com.mindagent.agent.service;

import com.mindagent.agent.config.RagProperties;
import com.mindagent.agent.entity.KnowledgeChunk;
import com.mindagent.agent.entity.KnowledgeDocument;
import com.mindagent.agent.repository.KnowledgeChunkRepository;
import com.mindagent.agent.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class KnowledgeBaseService {

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\p{IsHan}]");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".md", ".txt");
    private static final int MAX_CHUNK_SIZE = 900;
    private static final int CHUNK_OVERLAP = 120;
    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("(?s)^---\\s*\\n(.*?)\\n---\\s*\\n?(.*)$");
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^([A-Za-z0-9_\\-]+):\\s*(.*)$");
    private static final Pattern META_LINE_PATTERN = Pattern.compile("^\\*\\*(.+?)\\*\\*\\s*(.*)$");
    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile("(?m)^##\\s+(FAQ|KB)-(\\d+)\\s+(.+?)\\s*$");
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DELETED = "DELETED";
    private static final String KNOWLEDGE_BASE_1 = "knowledge_base_1";
    private static final String KNOWLEDGE_BASE_3 = "knowledge_base_3";
    private static final List<String> CONFIGURED_KNOWLEDGE_BASES = List.of(
            KNOWLEDGE_BASE_1,
            KNOWLEDGE_BASE_3
    );

    private final RagProperties ragProperties;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ObjectProvider<Bm25RetrievalService> bm25RetrievalServiceProvider;
    private volatile List<Chunk> chunks = List.of();
    private volatile List<Document> documents = List.of();

    @Autowired
    public KnowledgeBaseService(RagProperties ragProperties,
                                ObjectProvider<VectorStore> vectorStoreProvider,
                                KnowledgeDocumentRepository knowledgeDocumentRepository,
                                KnowledgeChunkRepository knowledgeChunkRepository,
                                ObjectMapper objectMapper,
                                PlatformTransactionManager transactionManager,
                                ObjectProvider<Bm25RetrievalService> bm25RetrievalServiceProvider) {
        this.ragProperties = ragProperties;
        this.vectorStoreProvider = vectorStoreProvider;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.bm25RetrievalServiceProvider = bm25RetrievalServiceProvider;
    }

    KnowledgeBaseService(RagProperties ragProperties,
                         ObjectProvider<VectorStore> vectorStoreProvider) {
        this(ragProperties, vectorStoreProvider, null, null, new ObjectMapper(), null, null);
    }

    public record KnowledgeSpace(String key, String title, String description) {
    }

    @PostConstruct
    public void init() {
        migrateAppointmentKnowledgeToInternalSpace();
        reload();
    }

    public synchronized void reload() {
        reload(true);
    }

    private synchronized void reload(boolean syncVectorStore) {
        List<Chunk> loadedChunks = new ArrayList<>();
        List<Document> loadedDocuments = new ArrayList<>();
        loadFromClasspath(loadedChunks, loadedDocuments);
        loadFromDatabase(loadedChunks, loadedDocuments);
        if (!loadedChunks.isEmpty()) {
            this.chunks = loadedChunks;
            this.documents = loadedDocuments;
            if (syncVectorStore && useChromaProvider()) {
                syncToChroma(loadedDocuments);
            }
        }
        refreshBm25Index();
    }

    private synchronized void refreshInMemoryCache() {
        reload(false);
    }

    private void migrateAppointmentKnowledgeToInternalSpace() {
        try {
            moveAppointmentFileIfPresent("faq", "appoint_faq.md");
            moveAppointmentFileIfPresent("kb", "appoint_kb.md");
            migrateAppointmentDocumentsInDatabase();
        } catch (Exception ignored) {
            // Keep startup resilient; retrieval can still work with the previous layout.
        }
    }

    private void moveAppointmentFileIfPresent(String kindDir, String fileName) throws Exception {
        Path source = resolveKnowledgeSpaceRootDir(KNOWLEDGE_BASE_1).resolve(kindDir).resolve(fileName).normalize();
        Path target = resolveKnowledgeSpaceRootDir(KNOWLEDGE_BASE_3).resolve(kindDir).resolve(fileName).normalize();
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void migrateAppointmentDocumentsInDatabase() {
        if (knowledgeDocumentRepository == null) {
            return;
        }
        List<KnowledgeDocument> sourceDocuments = knowledgeDocumentRepository.findAllByKnowledgeBaseKeyOrderByCreatedAtDesc(KNOWLEDGE_BASE_1);
        for (KnowledgeDocument document : sourceDocuments) {
            if (!shouldMoveToInternalSpace(document)) {
                continue;
            }
            executeInTransaction(() -> {
                document.setKnowledgeBaseKey(KNOWLEDGE_BASE_3);
                document.setStoragePath(resolveKnowledgeFilePath(KNOWLEDGE_BASE_3, document.getSourceType(), document.getStoredFilename()).toString());
                document.setStatus(STATUS_PROCESSING);
                document.setUpdatedAt(java.time.LocalDateTime.now());
                knowledgeDocumentRepository.save(document);
            });
            processDocument(document.getId(), false);
        }
    }

    private boolean shouldMoveToInternalSpace(KnowledgeDocument document) {
        if (document == null) {
            return false;
        }
        String storedFilename = firstNonBlank(document.getStoredFilename(), "").toLowerCase(Locale.ROOT);
        String docName = firstNonBlank(document.getDocName(), "").toLowerCase(Locale.ROOT);
        return storedFilename.startsWith("appoint_")
                || docName.contains("预约流程")
                || docName.contains("预约");
    }

    public KnowledgeDocument getKnowledgeDocument(Long documentId) {
        if (knowledgeDocumentRepository == null) {
            throw new IllegalStateException("Knowledge document repository is not configured.");
        }
        return knowledgeDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge document not found: " + documentId));
    }

    public List<KnowledgeDocument> listKnowledgeDocuments() {
        if (knowledgeDocumentRepository == null) {
            return List.of();
        }
        return knowledgeDocumentRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<KnowledgeChunk> listKnowledgeChunks(Long documentId) {
        if (knowledgeChunkRepository == null) {
            return List.of();
        }
        return knowledgeChunkRepository.findAllByDocumentIdOrderByChunkIndexAsc(documentId);
    }

    public List<KnowledgeSpace> listKnowledgeSpaces() {
        return List.of(
                new KnowledgeSpace(KNOWLEDGE_BASE_1, "上传校园心理健康知识", "面向校园心理健康知识、FAQ 与合并后的科普资料"),
                new KnowledgeSpace(KNOWLEDGE_BASE_3, "上传校园内部知识库", "面向校园内部制度、流程与资料")
        );
    }

    public KnowledgeDocument deleteKnowledgeDocument(Long documentId) {
        if (knowledgeDocumentRepository == null) {
            throw new IllegalStateException("Knowledge document repository is not configured.");
        }
        KnowledgeDocument document = knowledgeDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge document not found: " + documentId));
        deleteDocumentVectors(documentId);
        executeInTransaction(() -> {
            knowledgeChunkRepository.deleteAllByDocumentId(documentId);
            document.setStatus(STATUS_DELETED);
            document.setErrorMessage(null);
            document.setVectorCount(0);
            document.setChunkCount(0);
            document.setUpdatedAt(java.time.LocalDateTime.now());
            document.setProcessedAt(java.time.LocalDateTime.now());
            knowledgeDocumentRepository.save(document);
        });
        refreshInMemoryCache();
        return document;
    }

    public synchronized ImportResult importKnowledgeFile(String originalFilename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Knowledge file is empty.");
        }

        try {
            KnowledgeDocument document = registerKnowledgeDocument(originalFilename, content, false);
            processDocument(document.getId(), true);
            return new ImportResult(document.getStoredFilename(), content.length, size());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to import knowledge file: " + ex.getMessage(), ex);
        }
    }

    public List<String> retrieve(String query, int topK) {
        if (!ragProperties.isEnabled()) {
            return List.of();
        }

        if (useChromaProvider()) {
            List<String> chromaResult = retrieveFromChroma(query, topK);
            if (!chromaResult.isEmpty() || !ragProperties.isFallbackToLocal()) {
                return chromaResult;
            }
        }
        return retrieveFromLocal(query, topK);
    }

    public List<RetrievedChunk> retrieveVectorChunks(String query, RetrievalFilter filter, int topK) {
        if (!ragProperties.isEnabled()) {
            return List.of();
        }
        if (useChromaProvider()) {
            List<RetrievedChunk> chromaResults = retrieveStructuredFromChroma(query, filter, topK);
            if (!chromaResults.isEmpty() || !ragProperties.isFallbackToLocal()) {
                return chromaResults;
            }
        }
        return retrieveStructuredFromLocal(query, filter, topK);
    }

    public int size() {
        if (useChromaProvider() && !documents.isEmpty()) {
            return documents.size();
        }
        return chunks.size();
    }

    public synchronized KnowledgeDocument registerKnowledgeDocument(String originalFilename, byte[] content) throws Exception {
        return registerKnowledgeDocument(originalFilename, content, false, null, null);
    }

    public synchronized KnowledgeDocument registerKnowledgeDocument(String originalFilename,
                                                                    byte[] content,
                                                                    boolean existingFile) throws Exception {
        return registerKnowledgeDocument(originalFilename, content, existingFile, null, null);
    }

    public synchronized KnowledgeDocument registerKnowledgeDocument(String originalFilename,
                                                                    byte[] content,
                                                                    boolean existingFile,
                                                                    String sourceTypeHint) throws Exception {
        return registerKnowledgeDocument(originalFilename, content, existingFile, sourceTypeHint, null);
    }

    public synchronized KnowledgeDocument registerKnowledgeDocument(String originalFilename,
                                                                    byte[] content,
                                                                    boolean existingFile,
                                                                    String sourceTypeHint,
                                                                    String knowledgeBaseKey) throws Exception {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Knowledge file is empty.");
        }
        String fileName = sanitizeFileName(originalFilename);
        if (!isSupportedFileName(fileName)) {
            throw new IllegalArgumentException("Only .md or .txt files are supported.");
        }
        String normalizedKnowledgeBaseKey = normalizeKnowledgeBaseKey(knowledgeBaseKey);
        Path target = resolveKnowledgeFilePath(normalizedKnowledgeBaseKey, sourceTypeHint, fileName);
        Files.createDirectories(target.getParent());
        if (!target.startsWith(resolveKnowledgeDir())) {
            throw new IllegalArgumentException("Invalid file path.");
        }
        if (!existingFile) {
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else if (!Files.exists(target)) {
            throw new IllegalArgumentException("Knowledge file not found on disk: " + fileName);
        }

        KnowledgeDocument document = knowledgeDocumentRepository.findByKnowledgeBaseKeyAndStoredFilename(normalizedKnowledgeBaseKey, fileName)
                .orElseGet(KnowledgeDocument::new);
        document.setOriginalFilename(Paths.get(originalFilename).getFileName().toString());
        document.setStoredFilename(fileName);
        document.setStoragePath(target.toString());
        document.setKnowledgeBaseKey(normalizedKnowledgeBaseKey);
        document.setDocName(stripExtension(fileName));
        document.setSourceType(normalizeSourceType(sourceTypeHint, fileName, null));
        document.setStatus(STATUS_PROCESSING);
        document.setErrorMessage(null);
        document.setFileBytes((long) content.length);
        document.setChunkCount(0);
        document.setVectorCount(0);
        document.setUpdatedAt(java.time.LocalDateTime.now());
        knowledgeDocumentRepository.save(document);
        return document;
    }

    public synchronized KnowledgeDocument registerExistingKnowledgeDocument(Path sourceFile) throws Exception {
        if (sourceFile == null || !Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException("Knowledge file does not exist.");
        }
        byte[] content = Files.readAllBytes(sourceFile);
        return registerKnowledgeDocument(sourceFile.getFileName().toString(), content, true, null, inferKnowledgeBaseKey(sourceFile));
    }

    public int processDocument(Long documentId) {
        return processDocument(documentId, true);
    }

    public int processDocument(Long documentId, boolean refreshCache) {
        if (knowledgeDocumentRepository == null || knowledgeChunkRepository == null) {
            throw new IllegalStateException("Knowledge document repositories are not configured.");
        }
        KnowledgeDocument document = knowledgeDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge document not found: " + documentId));
        if (STATUS_ACTIVE.equalsIgnoreCase(document.getStatus()) && document.getChunkCount() != null && document.getChunkCount() > 0) {
            if (refreshCache) {
                refreshInMemoryCache();
            }
            return document.getChunkCount();
        }
        try {
            executeInTransaction(() -> {
                document.setStatus(STATUS_PROCESSING);
                document.setErrorMessage(null);
                document.setUpdatedAt(java.time.LocalDateTime.now());
                knowledgeDocumentRepository.save(document);
            });

            String raw = Files.readString(Paths.get(document.getStoragePath()), StandardCharsets.UTF_8);
            ParsedKnowledgeDocument parsed = parseKnowledgeDocument(
                    document.getStoredFilename(),
                    raw,
                    document.getSourceType()
            );
            List<Document> parsedDocuments = new ArrayList<>();
            List<Chunk> parsedChunks = new ArrayList<>();
            if (parsed.sections().isEmpty()) {
                parsedChunks.addAll(chunkGeneric(document.getStoredFilename(), raw.replace("\r", ""), parsedDocuments));
            } else {
                for (ParsedKnowledgeSection section : parsed.sections()) {
                    parsedChunks.addAll(chunkSection(parsed, section, parsedDocuments));
                }
            }
            List<KnowledgeChunk> chunkEntities = new ArrayList<>();
            for (int i = 0; i < parsedDocuments.size(); i++) {
                Document parsedDocument = parsedDocuments.get(i);
                Map<String, Object> metadata = new LinkedHashMap<>(parsedDocument.getMetadata());
                metadata.put("knowledge_base_key", document.getKnowledgeBaseKey());
                metadata.put("document_id", document.getId());
                metadata.put("chunk_index", i);
                metadata.put("token_count", parsedChunks.get(i).tokens.size());
                parsedDocuments.set(i, new Document(parsedDocument.getId(), parsedDocument.getText(), metadata));
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(document.getId());
                chunk.setChunkIndex(i);
                chunk.setChunkId(parsedDocument.getId());
                chunk.setContent(parsedDocument.getText());
                chunk.setMetadataJson(objectMapper.writeValueAsString(metadata));
                chunk.setTokenCount(parsedChunks.get(i).tokens.size());
                chunk.setSectionKey(firstNonBlank(stringMetadata(metadata, "section_key"), "generic"));
                chunk.setSectionTitle(firstNonBlank(stringMetadata(metadata, "section_title"), document.getDocName()));
                chunk.setCategory(stringMetadata(metadata, "category"));
                chunk.setTags(stringMetadata(metadata, "tags"));
                chunk.setRiskLevel(stringMetadata(metadata, "risk_level"));
                chunk.setSourcePageRange(stringMetadata(metadata, "source_page_range"));
                chunk.setQuestionText(stringMetadata(metadata, "question_text"));
                chunk.setAnswerText(stringMetadata(metadata, "answer_text"));
                chunkEntities.add(chunk);
            }
            if (chunkEntities.isEmpty()) {
                throw new IllegalStateException("No chunks were produced for knowledge document: " + document.getStoredFilename());
            }
            deleteDocumentVectors(document.getId());
            executeInTransaction(() -> {
                knowledgeChunkRepository.deleteAllByDocumentId(document.getId());
                knowledgeChunkRepository.saveAll(chunkEntities);

                document.setDocName(parsed.docName());
                document.setSourceType(parsed.sourceType());
                document.setAudience(parsed.audience());
                document.setVersion(parsed.version());
                document.setChunkCount(chunkEntities.size());
                document.setVectorCount(chunkEntities.size());
                document.setProcessedAt(java.time.LocalDateTime.now());
                document.setStatus(STATUS_ACTIVE);
                document.setUpdatedAt(java.time.LocalDateTime.now());
                knowledgeDocumentRepository.save(document);
            });

            if (useChromaProvider() && !parsedDocuments.isEmpty()) {
                appendToChroma(parsedDocuments);
            }

            if (refreshCache) {
                refreshInMemoryCache();
            }
            return chunkEntities.size();
        } catch (Exception ex) {
            executeInTransaction(() -> {
                document.setStatus(STATUS_FAILED);
                document.setErrorMessage(ex.getMessage() == null ? "unknown error" : ex.getMessage());
                document.setUpdatedAt(java.time.LocalDateTime.now());
                knowledgeDocumentRepository.save(document);
            });
            throw new IllegalStateException("Failed to process knowledge document: " + ex.getMessage(), ex);
        }
    }

    private void executeInTransaction(Runnable action) {
        if (transactionTemplate == null) {
            action.run();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    private <T> T executeInTransaction(Supplier<T> supplier) {
        if (transactionTemplate == null) {
            return supplier.get();
        }
        return transactionTemplate.execute(status -> supplier.get());
    }

    private void loadFromDatabase(List<Chunk> chunksOut, List<Document> docsOut) {
        if (knowledgeDocumentRepository == null || knowledgeChunkRepository == null) {
            return;
        }
        List<KnowledgeDocument> activeDocuments = knowledgeDocumentRepository.findAllByStatusOrderByCreatedAtDesc(STATUS_ACTIVE);
        if (activeDocuments.isEmpty()) {
            return;
        }
        Map<Long, KnowledgeDocument> documentsById = activeDocuments.stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, document -> document, (left, right) -> left, LinkedHashMap::new));
        List<Long> documentIds = new ArrayList<>(documentsById.keySet());
        List<KnowledgeChunk> rows = knowledgeChunkRepository.findAllByDocumentIdInOrderByDocumentIdAscChunkIndexAsc(documentIds);
        for (KnowledgeChunk row : rows) {
            KnowledgeDocument document = documentsById.get(row.getDocumentId());
            if (document == null) {
                continue;
            }
            Map<String, Object> metadata = parseMetadataJson(row.getMetadataJson());
            metadata.putIfAbsent("chunk_id", row.getChunkId());
            metadata.putIfAbsent("section_key", firstNonBlank(row.getSectionKey(), "generic"));
            metadata.putIfAbsent("section_title", firstNonBlank(row.getSectionTitle(), document.getDocName()));
            metadata.putIfAbsent("doc_name", firstNonBlank(document.getDocName(), stripExtension(document.getStoredFilename())));
            metadata.putIfAbsent("source_type", firstNonBlank(document.getSourceType(), "generic"));
            metadata.putIfAbsent("knowledge_base_key", firstNonBlank(document.getKnowledgeBaseKey(), KNOWLEDGE_BASE_1));
            metadata.putIfAbsent("document_id", document.getId());
            metadata.putIfAbsent("chunk_index", row.getChunkIndex());
            metadata.putIfAbsent("audience", firstNonBlank(document.getAudience(), ""));
            metadata.putIfAbsent("version", firstNonBlank(document.getVersion(), ""));
            if (row.getCategory() != null && !row.getCategory().isBlank()) {
                metadata.putIfAbsent("category", row.getCategory().trim());
            }
            if (row.getTags() != null && !row.getTags().isBlank()) {
                metadata.putIfAbsent("tags", row.getTags().trim());
            }
            if (row.getRiskLevel() != null && !row.getRiskLevel().isBlank()) {
                metadata.putIfAbsent("risk_level", row.getRiskLevel().trim());
            }
            if (row.getSourcePageRange() != null && !row.getSourcePageRange().isBlank()) {
                metadata.putIfAbsent("source_page_range", row.getSourcePageRange().trim());
            }
            if (row.getQuestionText() != null && !row.getQuestionText().isBlank()) {
                metadata.putIfAbsent("question_text", row.getQuestionText().trim());
            }
            if (row.getAnswerText() != null && !row.getAnswerText().isBlank()) {
                metadata.putIfAbsent("answer_text", row.getAnswerText().trim());
            }

            String content = row.getContent();
            chunksOut.add(new Chunk(row.getChunkId(), row.getSectionKey(), content, tokens(content)));
            docsOut.add(new Document(row.getChunkId(), content, metadata));
        }
    }

    private List<String> retrieveFromLocal(String query, int topK) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        Set<String> qTokens = tokens(query);
        return chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, similarity(qTokens, chunk.tokens)))
                .filter(scored -> scored.score > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(Math.max(1, topK))
                .map(scored -> scored.chunk.text)
                .collect(Collectors.toList());
    }

    private List<String> retrieveFromChroma(String query, int topK) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || documents.isEmpty()) {
            return List.of();
        }
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(1, topK))
                    .build();
            return vectorStore.similaritySearch(request)
                    .stream()
                    .map(Document::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<RetrievedChunk> retrieveStructuredFromLocal(String query, RetrievalFilter filter, int topK) {
        if (documents.isEmpty()) {
            return List.of();
        }
        Set<String> qTokens = tokens(query);
        List<RetrievedChunk> ranked = new ArrayList<>();
        for (Document document : documents) {
            RetrievedChunk chunk = documentToRetrievedChunk(document);
            if (chunk == null || !matchesFilter(chunk, filter)) {
                continue;
            }
            double score = similarity(qTokens, tokens(document.getText()));
            if (score <= 0) {
                continue;
            }
            ranked.add(chunk.withVector(score, 0));
        }
        ranked.sort(Comparator.comparingDouble(RetrievedChunk::vectorScore).reversed());
        return toVectorRanks(ranked, topK);
    }

    private List<RetrievedChunk> retrieveStructuredFromChroma(String query, RetrievalFilter filter, int topK) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || documents.isEmpty()) {
            return List.of();
        }
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(1, topK) * 3)
                    .build();
            List<RetrievedChunk> matched = vectorStore.similaritySearch(request).stream()
                    .map(this::documentToRetrievedChunk)
                    .filter(chunk -> chunk != null && matchesFilter(chunk, filter))
                    .collect(Collectors.toList());
            return toVectorRanks(matched, topK);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<RetrievedChunk> toVectorRanks(List<RetrievedChunk> matched, int topK) {
        int limit = Math.max(1, topK);
        List<RetrievedChunk> ranked = new ArrayList<>();
        for (int i = 0; i < matched.size() && i < limit; i++) {
            RetrievedChunk chunk = matched.get(i);
            double score = chunk.vectorScore() > 0 ? chunk.vectorScore() : (limit - i);
            ranked.add(chunk.withVector(score, i + 1));
        }
        return ranked;
    }

    private List<Chunk> chunk(String source, String raw, List<Document> docsOut) {
        return chunkKnowledgeFile(source, raw, docsOut);
    }

    private List<Chunk> chunkKnowledgeFile(String source, String raw, List<Document> docsOut) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        String normalized = raw.replace("\r", "");
        ParsedKnowledgeDocument document = parseKnowledgeDocument(source, normalized);
        if (document.sections().isEmpty()) {
            return chunkGeneric(source, normalized, docsOut);
        }

        List<Chunk> out = new ArrayList<>();
        for (ParsedKnowledgeSection section : document.sections()) {
            out.addAll(chunkSection(document, section, docsOut));
        }
        return out;
    }

    private List<Chunk> chunkGeneric(String source, String raw, List<Document> docsOut) {
        String docName = source == null ? "knowledge" : stripExtension(source);
        List<String> parts = packChunks(splitPreservingStructure(raw), MAX_CHUNK_SIZE, CHUNK_OVERLAP);
        List<Chunk> out = new ArrayList<>();
        int index = 0;
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() < ragProperties.getMinChunkLength()) {
                continue;
            }
            String chunkId = buildChunkId(source, "generic", index++);
            Map<String, Object> metadata = baseMetadata(docName, source, "generic", chunkId, null);
            out.add(new Chunk(chunkId, source, trimmed, tokens(trimmed)));
            docsOut.add(new Document(chunkId, trimmed, metadata));
        }
        return out;
    }

    private List<Chunk> chunkSection(ParsedKnowledgeDocument document, ParsedKnowledgeSection section, List<Document> docsOut) {
        List<Chunk> out = new ArrayList<>();
        String body = section.bodyText().isBlank() ? "" : section.bodyText();
        String prefix = buildBasePrefix(document, section);
        String answerPrefix = section.questionText().isBlank() ? "" : "### 问题\n" + section.questionText().trim() + "\n\n";
        if (!section.answerText().isBlank()) {
            answerPrefix += "### 回答\n";
        }
        int availableSize = Math.max(200, MAX_CHUNK_SIZE - (prefix.length() + answerPrefix.length()));
        int availableOverlap = Math.min(CHUNK_OVERLAP, Math.max(0, availableSize / 3));
        List<String> parts = packChunks(splitPreservingStructure(body), availableSize, availableOverlap);
        if (parts.isEmpty()) {
            parts = List.of("");
        }

        for (int i = 0; i < parts.size(); i++) {
            String bodyPart = parts.get(i).trim();
            String content = (prefix + answerPrefix + bodyPart).trim();
            if (content.length() < ragProperties.getMinChunkLength()) {
                continue;
            }
            String chunkId = buildChunkId(document.sourceFileName(), section.sectionKey(), i);
            Map<String, Object> metadata = baseMetadata(document.docName(), document.sourceFileName(), document.sourceType(), chunkId, section);
            out.add(new Chunk(chunkId, document.sourceFileName(), content, tokens(content)));
            docsOut.add(new Document(chunkId, content, metadata));
        }
        return out;
    }

    private String buildBasePrefix(ParsedKnowledgeDocument document, ParsedKnowledgeSection section) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(document.docName()).append('\n');
        sb.append("## ").append(section.sectionKey()).append(' ').append(section.sectionTitle()).append('\n');
        appendIfNotBlank(sb, "**doc_name:** ", document.docName());
        appendIfNotBlank(sb, "**source_type:** ", document.sourceType());
        appendIfNotBlank(sb, "**audience:** ", document.audience());
        appendIfNotBlank(sb, "**version:** ", document.version());
        appendIfNotBlank(sb, "**category:** ", section.category());
        appendIfNotBlank(sb, "**tags:** ", section.tags());
        appendIfNotBlank(sb, "**risk_level:** ", section.riskLevel());
        appendIfNotBlank(sb, "**source_page_range:** ", section.sourcePageRange());
        sb.append('\n');
        return sb.toString();
    }

    private void appendIfNotBlank(StringBuilder sb, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(prefix).append(value.trim()).append('\n');
        }
    }

    private List<String> splitPreservingStructure(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.replace("\r", "").trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> blocks = new ArrayList<>();
        Matcher headingMatcher = Pattern.compile("(?m)^#{1,6}\\s+.*$").matcher(normalized);
        int cursor = 0;
        while (headingMatcher.find()) {
            if (headingMatcher.start() > cursor) {
                String before = normalized.substring(cursor, headingMatcher.start()).trim();
                if (!before.isBlank()) {
                    blocks.add(before);
                }
            }
            cursor = headingMatcher.start();
        }
        if (cursor < normalized.length()) {
            String tail = normalized.substring(cursor).trim();
            if (!tail.isBlank()) {
                blocks.add(tail);
            }
        }

        if (blocks.isEmpty()) {
            blocks = Arrays.stream(normalized.split("\\n\\s*\\n"))
                    .map(String::trim)
                    .filter(part -> !part.isBlank())
                    .collect(Collectors.toList());
        }

        List<String> out = new ArrayList<>();
        for (String block : blocks) {
            out.addAll(splitBlock(block, MAX_CHUNK_SIZE, CHUNK_OVERLAP));
        }
        return out;
    }

    private List<String> splitBlock(String text, int maxSize, int overlap) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            return List.of();
        }
        if (trimmed.length() <= maxSize) {
            return List.of(trimmed);
        }

        List<String> paragraphBlocks = Arrays.stream(trimmed.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .collect(Collectors.toList());
        if (paragraphBlocks.size() > 1) {
            return packChunks(paragraphBlocks, maxSize, overlap);
        }

        List<String> lineBlocks = Arrays.stream(trimmed.split("\\n"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .collect(Collectors.toList());
        if (lineBlocks.size() > 1) {
            return packChunks(lineBlocks, maxSize, overlap);
        }

        List<String> sentenceBlocks = Arrays.stream(trimmed.split("(?<=[。！？!?；;])"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .collect(Collectors.toList());
        if (sentenceBlocks.size() > 1) {
            return packChunks(sentenceBlocks, maxSize, overlap);
        }

        return hardSplit(trimmed, maxSize, overlap);
    }

    private List<String> packChunks(List<String> blocks, int maxSize, int overlap) {
        if (blocks.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.length() > maxSize) {
                if (current.length() > 0) {
                    out.add(current.toString().trim());
                    current.setLength(0);
                }
                out.addAll(splitBlock(trimmed, maxSize, overlap));
                continue;
            }
            if (current.length() == 0) {
                current.append(trimmed);
                continue;
            }
            String candidate = current + "\n\n" + trimmed;
            if (candidate.length() <= maxSize) {
                current.append("\n\n").append(trimmed);
            } else {
                out.add(current.toString().trim());
                current.setLength(0);
                current.append(trimmed);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString().trim());
        }
        return applyOverlap(out, overlap);
    }

    private List<String> applyOverlap(List<String> chunks, int overlap) {
        if (chunks.size() <= 1 || overlap <= 0) {
            return chunks;
        }
        List<String> out = new ArrayList<>();
        String previous = null;
        for (String current : chunks) {
            String trimmed = current == null ? "" : current.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (previous == null) {
                out.add(trimmed);
            } else {
                String overlapText = tail(previous, overlap);
                if (trimmed.startsWith(overlapText)) {
                    out.add(trimmed);
                } else {
                    out.add((overlapText + "\n\n" + trimmed).trim());
                }
            }
            previous = trimmed;
        }
        return out;
    }

    private String tail(String value, int overlap) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int start = Math.max(0, value.length() - overlap);
        return value.substring(start).trim();
    }

    private List<String> hardSplit(String text, int maxSize, int overlap) {
        if (text.length() <= maxSize) {
            return List.of(text);
        }
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxSize);
            String part = text.substring(start, end).trim();
            if (!part.isBlank()) {
                out.add(part);
            }
            if (end == text.length()) {
                break;
            }
            start = Math.max(0, end - overlap);
            if (start >= end) {
                start = end;
            }
        }
        return out;
    }

    private String buildChunkId(String source, String sectionKey, int index) {
        String safeSource = sanitizeIdPart(source == null ? "knowledge" : source);
        String safeSection = sanitizeIdPart(sectionKey == null ? "section" : sectionKey);
        return safeSource + "__" + safeSection + "__" + String.format(Locale.ROOT, "%03d", index);
    }

    private String sanitizeIdPart(String value) {
        String safe = value == null ? "" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.isBlank()) {
            return "chunk";
        }
        return safe;
    }

    private Map<String, Object> baseMetadata(String docName,
                                             String sourceFileName,
                                             String sourceType,
                                             String chunkId,
                                             ParsedKnowledgeSection section) {
        return baseMetadata(docName, sourceFileName, sourceType, chunkId, section, inferKnowledgeBaseKey(sourceFileName == null ? null : Paths.get(sourceFileName)), null, null);
    }

    private Map<String, Object> baseMetadata(String docName,
                                             String sourceFileName,
                                             String sourceType,
                                             String chunkId,
                                             ParsedKnowledgeSection section,
                                             String knowledgeBaseKey,
                                             Long documentId,
                                             Integer chunkIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfNotBlank(metadata, "doc_name", docName);
        putIfNotBlank(metadata, "source_type", sourceType);
        putIfNotBlank(metadata, "chunk_id", chunkId);
        putIfNotBlank(metadata, "knowledge_base_key", knowledgeBaseKey);
        if (documentId != null) {
            metadata.put("document_id", documentId);
        }
        if (chunkIndex != null) {
            metadata.put("chunk_index", chunkIndex);
        }
        putIfNotBlank(metadata, "section_key", section == null ? "generic" : section.sectionKey());
        putIfNotBlank(metadata, "section_title", section == null ? null : section.sectionTitle());
        putIfNotBlank(metadata, "category", section == null ? null : section.category());
        putIfNotBlank(metadata, "tags", section == null ? null : section.tags());
        putIfNotBlank(metadata, "risk_level", section == null ? null : section.riskLevel());
        putIfNotBlank(metadata, "audience", section == null ? null : section.audience());
        putIfNotBlank(metadata, "version", section == null ? null : section.version());
        putIfNotBlank(metadata, "source_page_range", section == null ? null : section.sourcePageRange());
        putIfNotBlank(metadata, "question_text", section == null ? null : section.questionText());
        putIfNotBlank(metadata, "answer_text", section == null ? null : section.answerText());
        putIfNotBlank(metadata, "source_file", sourceFileName);
        return metadata;
    }

    private void putIfNotBlank(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value.trim());
        }
    }

    private Map<String, Object> parseMetadataJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {
            });
            return metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String stringMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return "";
        }
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String readText(Resource resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private void loadFromClasspath(List<Chunk> chunksOut, List<Document> docsOut) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Set<String> seen = new HashSet<>();
        try {
            loadResources(resolver, seen, "classpath*:knowledge/**/*.md", chunksOut, docsOut);
            loadResources(resolver, seen, "classpath*:knowledge/**/*.txt", chunksOut, docsOut);
            loadResources(resolver, seen, "classpath*:knowledge/*.md", chunksOut, docsOut);
            loadResources(resolver, seen, "classpath*:knowledge/*.txt", chunksOut, docsOut);
        } catch (Exception ignored) {
            // Keep existing chunks if reload fails.
        }
    }

    private void loadResources(PathMatchingResourcePatternResolver resolver,
                               Set<String> seen,
                               String pattern,
                               List<Chunk> chunksOut,
                               List<Document> docsOut) throws Exception {
        Resource[] resources = resolver.getResources(pattern);
        for (Resource resource : resources) {
            if (!resource.exists()) {
                continue;
            }
            String fingerprint = resource.getDescription();
            if (!seen.add(fingerprint)) {
                continue;
            }
            String fileName = resource.getFilename();
            if (!isSupportedFileName(fileName)) {
                continue;
            }
            String text = readText(resource);
            chunksOut.addAll(chunk(fileName, text, docsOut));
        }
    }

    private void loadFromFileSystem(List<Chunk> chunksOut, List<Document> docsOut) {
        for (String knowledgeBaseKey : CONFIGURED_KNOWLEDGE_BASES) {
            for (String kindDir : List.of("faq", "kb")) {
                Path targetDir = resolveKnowledgeSpaceRootDir(knowledgeBaseKey).resolve(kindDir);
                if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
                    continue;
                }
                try (Stream<Path> stream = Files.walk(targetDir)) {
                    List<Path> files = stream
                            .filter(Files::isRegularFile)
                            .filter(path -> isSupportedFileName(path.getFileName().toString()))
                            .sorted(Comparator.comparing(Path::toString))
                            .collect(Collectors.toList());
                    for (Path file : files) {
                        String text = Files.readString(file, StandardCharsets.UTF_8);
                        chunksOut.addAll(chunk(file.getFileName().toString(), text, docsOut));
                    }
                } catch (Exception ignored) {
                    // Keep existing chunks if filesystem reload fails.
                }
            }
        }
    }

    private Path resolveKnowledgeDir() {
        String configured = ragProperties.getKnowledgeDir();
        if (configured == null || configured.isBlank()) {
            configured = "backend/storage/knowledge";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private Path resolveKnowledgeSpaceRootDir(String knowledgeBaseKey) {
        return resolveKnowledgeDir().resolve(knowledgeBaseFolderName(knowledgeBaseKey)).normalize();
    }

    private Path resolveKnowledgeFilePath(String knowledgeBaseKey, String sourceTypeHint, String fileName) {
        Path baseDir = resolveKnowledgeSpaceRootDir(knowledgeBaseKey);
        String kindDir = "faq".equalsIgnoreCase(normalizeSourceType(sourceTypeHint, fileName, null)) ? "faq" : "kb";
        return baseDir.resolve(kindDir).resolve(fileName).normalize();
    }

    private String inferKnowledgeBaseKey(Path sourceFile) {
        if (sourceFile == null) {
            return KNOWLEDGE_BASE_1;
        }
        String normalized = sourceFile.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.contains("/knowledge_base_3/") || normalized.contains("/knowlegde_base_3/")) {
            return KNOWLEDGE_BASE_3;
        }
        return KNOWLEDGE_BASE_1;
    }

    private String knowledgeBaseFolderName(String knowledgeBaseKey) {
        return switch (normalizeKnowledgeBaseKey(knowledgeBaseKey)) {
            case KNOWLEDGE_BASE_3 -> "knowledge_base_3";
            default -> "knowlegde_base_1";
        };
    }

    private String normalizeKnowledgeBaseKey(String knowledgeBaseKey) {
        String normalized = knowledgeBaseKey == null ? "" : knowledgeBaseKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return KNOWLEDGE_BASE_1;
        }
        if (normalized.contains("3")) {
            return KNOWLEDGE_BASE_3;
        }
        return KNOWLEDGE_BASE_1;
    }

    public String knowledgeBaseLabel(String knowledgeBaseKey) {
        return switch (normalizeKnowledgeBaseKey(knowledgeBaseKey)) {
            case KNOWLEDGE_BASE_3 -> "上传校园内部知识库";
            default -> "上传校园心理健康知识";
        };
    }

    private String sanitizeFileName(String fileName) {
        String raw = fileName == null ? "" : Paths.get(fileName).getFileName().toString();
        String sanitized = raw.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank()) {
            sanitized = "knowledge_" + System.currentTimeMillis() + ".md";
        }
        return sanitized;
    }

    private boolean isSupportedFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);

        Matcher wordMatcher = WORD_PATTERN.matcher(normalized);
        while (wordMatcher.find()) {
            out.add(wordMatcher.group());
        }

        List<String> cjkChars = new ArrayList<>();
        Matcher cjkMatcher = CJK_PATTERN.matcher(normalized);
        while (cjkMatcher.find()) {
            cjkChars.add(cjkMatcher.group());
        }

        for (int i = 0; i < cjkChars.size(); i++) {
            out.add(cjkChars.get(i));
            if (i + 1 < cjkChars.size()) {
                out.add(cjkChars.get(i) + cjkChars.get(i + 1));
            }
        }
        return out;
    }

    private double similarity(Set<String> q, Set<String> c) {
        if (q.isEmpty() || c.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (String token : q) {
            if (c.contains(token)) {
                hit++;
            }
        }
        if (hit == 0) {
            return 0;
        }
        return hit / Math.sqrt((double) q.size() * c.size());
    }

    private boolean useChromaProvider() {
        return "chroma".equalsIgnoreCase(ragProperties.getProvider());
    }

    private RetrievedChunk documentToRetrievedChunk(Document document) {
        if (document == null) {
            return null;
        }
        Map<String, Object> metadata = document.getMetadata();
        String content = document.getText() == null ? "" : document.getText();
        return new RetrievedChunk(
                firstNonBlank(stringMetadata(metadata, "chunk_id"), document.getId()),
                longMetadata(metadata, "document_id"),
                firstNonBlank(stringMetadata(metadata, "knowledge_base_key"), KNOWLEDGE_BASE_1),
                firstNonBlank(stringMetadata(metadata, "doc_name"), "knowledge"),
                firstNonBlank(stringMetadata(metadata, "source_type"), "generic"),
                stringMetadata(metadata, "category"),
                intMetadata(metadata, "token_count", tokens(content).size()),
                content,
                0d,
                0,
                scoreMetadata(metadata, "distance"),
                0,
                0d,
                0d,
                0
        );
    }

    private boolean matchesFilter(RetrievedChunk chunk, RetrievalFilter filter) {
        if (filter == null) {
            return true;
        }
        return matchValue(chunk.knowledgeBaseKey(), filter.knowledgeBaseKeys())
                && matchValue(chunk.category(), filter.categories())
                && matchValue(chunk.sourceType(), filter.sourceTypes());
    }

    private boolean matchValue(String actual, Set<String> expected) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (actual == null || actual.isBlank()) {
            return false;
        }
        for (String item : expected) {
            if (actual.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }

    private Long longMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int intMetadata(Map<String, Object> metadata, String key, int fallback) {
        if (metadata == null) {
            return fallback;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double scoreMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return 0d;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return 0d;
            }
        }
        return 0d;
    }

    private void refreshBm25Index() {
        Bm25RetrievalService service = bm25RetrievalServiceProvider == null ? null : bm25RetrievalServiceProvider.getIfAvailable();
        if (service != null) {
            service.refresh();
        }
    }

    private void syncToChroma(List<Document> loadedDocuments) {
        syncToChroma(loadedDocuments, true);
    }

    private void appendToChroma(List<Document> documentsToAdd) {
        syncToChroma(documentsToAdd, false);
    }

    private void syncToChroma(List<Document> loadedDocuments, boolean replaceCollection) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || loadedDocuments.isEmpty()) {
            return;
        }
        try {
            if (replaceCollection) {
                clearVectorStore(vectorStore);
            }
            vectorStore.add(loadedDocuments);
        } catch (Exception ignored) {
            // Fallback to local retrieval if Chroma sync fails.
        }
    }

    private void deleteDocumentVectors(Long documentId) {
        if (knowledgeChunkRepository == null) {
            return;
        }
        List<String> ids = knowledgeChunkRepository.findAllByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .map(KnowledgeChunk::getChunkId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return;
        }
        try {
            vectorStore.delete(ids);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to delete knowledge chunks from vector store: " + ex.getMessage(), ex);
        }
    }

    private void clearVectorStore(VectorStore vectorStore) {
        try {
            vectorStore.getClass().getMethod("deleteCollection").invoke(vectorStore);
            return;
        } catch (Exception ignored) {
            // Fall back to best-effort id deletion.
        }
        List<String> ids = documents.stream()
                .map(Document::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(ids);
        } catch (Exception ignored) {
            // Best effort only.
        }
    }

    private ParsedKnowledgeDocument parseKnowledgeDocument(String sourceFileName, String raw) {
        return parseKnowledgeDocument(sourceFileName, raw, null);
    }

    private ParsedKnowledgeDocument parseKnowledgeDocument(String sourceFileName, String raw, String sourceTypeHint) {
        Map<String, String> frontMatter = parseFrontMatter(raw);
        String docName = firstNonBlank(frontMatter.get("title"), stripExtension(sourceFileName));
        String sourceType = normalizeSourceType(firstNonBlank(sourceTypeHint, frontMatter.get("source_type")), sourceFileName, raw);
        String audience = firstNonBlank(frontMatter.get("audience"), "");
        String version = firstNonBlank(frontMatter.get("version"), "");

        if (containsFaqSections(raw)) {
            return parseFaqDocument(sourceFileName, docName, sourceType, audience, version, raw);
        }
        if (containsKbSections(raw)) {
            return parseKbDocument(sourceFileName, docName, sourceType, audience, version, raw);
        }
        return new ParsedKnowledgeDocument(sourceFileName, docName, sourceType, audience, version, List.of());
    }

    private ParsedKnowledgeDocument parseFaqDocument(String sourceFileName,
                                                     String docName,
                                                     String sourceType,
                                                     String audience,
                                                     String version,
                                                     String raw) {
        List<ParsedKnowledgeSection> sections = new ArrayList<>();
        for (SectionSlice slice : splitSections(raw, "FAQ")) {
            Map<String, String> sectionMeta = parseSectionMetadata(slice.body());
            String questionText = extractSectionBlock(slice.body(), "问题");
            String answerText = extractSectionBlock(slice.body(), "回答");
            String bodyText = answerText.isBlank() ? stripSectionIntro(slice.body()) : answerText;
            sections.add(new ParsedKnowledgeSection(
                    slice.sectionKey(),
                    slice.sectionTitle(),
                    firstNonBlank(sectionMeta.get("category"), ""),
                    firstNonBlank(sectionMeta.get("tags"), ""),
                    firstNonBlank(sectionMeta.get("risk_level"), ""),
                    audience,
                    version,
                    "",
                    questionText.isBlank() ? slice.sectionTitle() : questionText,
                    answerText,
                    bodyText
            ));
        }
        return new ParsedKnowledgeDocument(sourceFileName, docName, sourceType, audience, version, sections);
    }

    private ParsedKnowledgeDocument parseKbDocument(String sourceFileName,
                                                    String docName,
                                                    String sourceType,
                                                    String audience,
                                                    String version,
                                                    String raw) {
        List<ParsedKnowledgeSection> sections = new ArrayList<>();
        for (SectionSlice slice : splitSections(raw, "KB")) {
            Map<String, String> sectionMeta = parseSectionMetadata(slice.body());
            String bodyText = stripSectionIntro(slice.body());
            sections.add(new ParsedKnowledgeSection(
                    slice.sectionKey(),
                    slice.sectionTitle(),
                    firstNonBlank(sectionMeta.get("category"), ""),
                    firstNonBlank(sectionMeta.get("tags"), ""),
                    firstNonBlank(sectionMeta.get("risk_level"), ""),
                    audience,
                    version,
                    firstNonBlank(sectionMeta.get("source_page_range"), ""),
                    "",
                    "",
                    bodyText
            ));
        }
        return new ParsedKnowledgeDocument(sourceFileName, docName, sourceType, audience, version, sections);
    }

    private boolean containsFaqSections(String raw) {
        return raw != null && raw.contains("## FAQ-");
    }

    private boolean containsKbSections(String raw) {
        return raw != null && raw.contains("## KB-");
    }

    private List<SectionSlice> splitSections(String raw, String prefix) {
        String normalized = raw.replace("\r", "");
        Matcher matcher = Pattern.compile("(?m)^##\\s+(" + prefix + "-\\d+)\\s+(.+?)\\s*$").matcher(normalized);
        List<Integer> starts = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            keys.add(matcher.group(1));
            titles.add(matcher.group(2).trim());
        }

        List<SectionSlice> slices = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : normalized.length();
            String body = normalized.substring(start, end).trim();
            slices.add(new SectionSlice(keys.get(i), titles.get(i), body));
        }
        return slices;
    }

    private Map<String, String> parseFrontMatter(String raw) {
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(raw.replace("\r", ""));
        if (!matcher.find()) {
            return Map.of();
        }
        String frontMatterText = matcher.group(1);
        Map<String, String> out = new HashMap<>();
        for (String line : frontMatterText.split("\\n")) {
            Matcher kv = KEY_VALUE_PATTERN.matcher(line.trim());
            if (kv.find()) {
                out.put(kv.group(1).trim(), kv.group(2).trim());
            }
        }
        return out;
    }

    private Map<String, String> parseSectionMetadata(String sectionBody) {
        Map<String, String> out = new HashMap<>();
        for (String line : sectionBody.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            Matcher matcher = META_LINE_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                String key = matcher.group(1).trim();
                if (key.endsWith(":")) {
                    key = key.substring(0, key.length() - 1).trim();
                }
                out.put(key, matcher.group(2).trim());
                continue;
            }
            if (trimmed.startsWith("### ")) {
                break;
            }
        }
        return out;
    }

    private String extractSectionBlock(String sectionBody, String heading) {
        String normalized = sectionBody.replace("\r", "");
        Pattern pattern = Pattern.compile("(?ms)^###\\s+" + Pattern.quote(heading) + "\\s*\\n(.*?)(?=^###\\s+|\\z)");
        Matcher matcher = pattern.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private String stripSectionIntro(String sectionBody) {
        String normalized = sectionBody.replace("\r", "");
        String[] lines = normalized.split("\\n");
        List<String> out = new ArrayList<>();
        boolean seenContent = false;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (!seenContent) {
                if (trimmed.isBlank()) {
                    continue;
                }
                if (trimmed.startsWith("**") && trimmed.contains(":")) {
                    continue;
                }
                seenContent = true;
                out.add(line);
                continue;
            }
            out.add(line);
        }
        return String.join("\n", out).trim();
    }

    private String normalizeSourceType(String sourceType, String sourceFileName, String raw) {
        if (sourceType != null && !sourceType.isBlank()) {
            String normalized = sourceType.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("faq")) {
                return "faq";
            }
            if (normalized.contains("kb") || normalized.contains("article")) {
                return "kb";
            }
        }
        if (containsFaqSections(raw)) {
            return "faq";
        }
        if (containsKbSections(raw)) {
            return "kb";
        }
        String lower = sourceFileName == null ? "" : sourceFileName.toLowerCase(Locale.ROOT);
        if (lower.contains("faq")) {
            return "faq";
        }
        if (lower.contains("kb")) {
            return "kb";
        }
        return "generic";
    }

    private String stripExtension(String value) {
        if (value == null) {
            return "";
        }
        int idx = value.lastIndexOf('.');
        if (idx <= 0) {
            return value;
        }
        return value.substring(0, idx);
    }

    private String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static class Chunk {
        private final String id;
        private final String source;
        private final String text;
        private final Set<String> tokens;

        private Chunk(String id, String source, String text, Set<String> tokens) {
            this.id = id;
            this.source = source;
            this.text = text;
            this.tokens = tokens;
        }
    }

    public record ImportResult(String fileName, int bytes, int chunks) {
    }

    private static class ScoredChunk {
        private final Chunk chunk;
        private final double score;

        private ScoredChunk(Chunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }

        private double score() {
            return score;
        }
    }

    private record SectionSlice(String sectionKey, String sectionTitle, String body) {
    }

    private record ParsedKnowledgeDocument(String sourceFileName,
                                           String docName,
                                           String sourceType,
                                           String audience,
                                           String version,
                                           List<ParsedKnowledgeSection> sections) {
    }

    private record ParsedKnowledgeSection(String sectionKey,
                                          String sectionTitle,
                                          String category,
                                          String tags,
                                          String riskLevel,
                                          String audience,
                                          String version,
                                          String sourcePageRange,
                                          String questionText,
                                          String answerText,
                                          String bodyText) {
    }
}
