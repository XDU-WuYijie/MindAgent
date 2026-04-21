package com.mindbridge.agent.service;

import com.mindbridge.agent.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class KnowledgeBaseService {

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\p{IsHan}]");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".md", ".txt");

    private final RagProperties ragProperties;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private volatile List<Chunk> chunks = List.of();
    private volatile List<Document> documents = List.of();
    private volatile List<String> indexedDocumentIds = List.of();

    public KnowledgeBaseService(RagProperties ragProperties,
                                ObjectProvider<VectorStore> vectorStoreProvider) {
        this.ragProperties = ragProperties;
        this.vectorStoreProvider = vectorStoreProvider;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        List<Chunk> loaded = new ArrayList<>();
        List<Document> loadedDocuments = new ArrayList<>();
        loadFromClasspath(loaded, loadedDocuments);
        loadFromFileSystem(loaded, loadedDocuments);
        if (!loaded.isEmpty()) {
            this.chunks = loaded;
            this.documents = loadedDocuments;
            if (useChromaProvider()) {
                syncToChroma(loadedDocuments);
            }
        }
    }

    public synchronized ImportResult importKnowledgeFile(String originalFilename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Knowledge file is empty.");
        }

        String fileName = sanitizeFileName(originalFilename);
        if (!isSupportedFileName(fileName)) {
            throw new IllegalArgumentException("Only .md or .txt files are supported.");
        }

        try {
            Path dir = resolveKnowledgeDir();
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName).normalize();
            if (!target.startsWith(dir)) {
                throw new IllegalArgumentException("Invalid file path.");
            }
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            reload();
            return new ImportResult(target.getFileName().toString(), content.length, size());
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

    public int size() {
        if (useChromaProvider() && !documents.isEmpty()) {
            return documents.size();
        }
        return chunks.size();
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

    private List<Chunk> chunk(String source, String raw, List<Document> docsOut) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.replace("\r", "");
        String[] parts = normalized.split("\n\\s*\n");
        List<Chunk> out = new ArrayList<>();
        int index = 0;
        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.length() < ragProperties.getMinChunkLength()) {
                continue;
            }
            String docId = buildDocId(source, index++);
            out.add(new Chunk(docId, source, trimmed, tokens(trimmed)));
            docsOut.add(new Document(
                    docId,
                    trimmed,
                    Map.of("source", source == null ? "unknown" : source)
            ));
        }
        return out;
    }

    private String buildDocId(String source, int index) {
        String safeSource = source == null ? "knowledge" : source.replaceAll("[^a-zA-Z0-9_-]", "_");
        return safeSource + "_" + index;
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
        try {
            Resource[] resources = resolver.getResources("classpath*:knowledge/*.*");
            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }
                String fileName = resource.getFilename();
                if (!isSupportedFileName(fileName)) {
                    continue;
                }
                String text = readText(resource);
                chunksOut.addAll(chunk(fileName, text, docsOut));
            }
        } catch (Exception ignored) {
            // Keep existing chunks if reload fails.
        }
    }

    private void loadFromFileSystem(List<Chunk> chunksOut, List<Document> docsOut) {
        Path dir = resolveKnowledgeDir();
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> isSupportedFileName(path.getFileName().toString()))
                    .collect(Collectors.toList());
            for (Path file : files) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                chunksOut.addAll(chunk(file.getFileName().toString(), text, docsOut));
            }
        } catch (Exception ignored) {
            // Keep existing chunks if filesystem reload fails.
        }
    }

    private Path resolveKnowledgeDir() {
        String configured = ragProperties.getKnowledgeDir();
        if (configured == null || configured.isBlank()) {
            configured = "./data/knowledge";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
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

    private void syncToChroma(List<Document> loadedDocuments) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || loadedDocuments.isEmpty()) {
            return;
        }
        try {
            if (!indexedDocumentIds.isEmpty()) {
                vectorStore.delete(indexedDocumentIds);
            }
            vectorStore.add(loadedDocuments);
            indexedDocumentIds = loadedDocuments.stream()
                    .map(Document::getId)
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            // Fallback to local retrieval if Chroma sync fails.
        }
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
}
