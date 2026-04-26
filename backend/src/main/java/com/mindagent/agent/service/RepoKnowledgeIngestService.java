package com.mindagent.agent.service;

import com.mindagent.agent.config.RagProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RepoKnowledgeIngestService {

    private static final int MAX_IMPORT_FILES = 200;
    private static final long MAX_FILE_BYTES = 1024L * 1024L;
    private static final long GIT_TIMEOUT_SECONDS = 90L;
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[^a-zA-Z0-9._-]");

    private final RagProperties ragProperties;
    private final KnowledgeBaseService knowledgeBaseService;

    public RepoKnowledgeIngestService(RagProperties ragProperties,
                                      KnowledgeBaseService knowledgeBaseService) {
        this.ragProperties = ragProperties;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public synchronized PullResult pullRepo(String repoUrl, String branch, String subPath) {
        String normalizedRepoUrl = normalizeRequired(repoUrl, "repoUrl is required");
        String normalizedBranch = normalizeBranch(branch);
        String normalizedSubPath = normalizeSubPath(subPath);
        String repoKey = shortHash(normalizedRepoUrl + "#" + normalizedBranch);

        Path cacheRoot = Paths.get("./storage/repo-cache").toAbsolutePath().normalize();
        Path repoDir = cacheRoot.resolve(repoKey).normalize();
        Path knowledgeDir = resolveKnowledgeDir();

        try {
            Files.createDirectories(cacheRoot);
            Files.createDirectories(knowledgeDir);

            syncRepository(normalizedRepoUrl, normalizedBranch, repoDir);

            Path sourceRoot = resolveSourceRoot(repoDir, normalizedSubPath);
            String targetPrefix = "repo_" + repoKey + "__";
            cleanupOldImportedFiles(knowledgeDir, targetPrefix);

            ImportStats importStats = copyKnowledgeFiles(sourceRoot, knowledgeDir, targetPrefix);
            knowledgeBaseService.reload();

            return new PullResult(
                    normalizedRepoUrl,
                    normalizedBranch,
                    normalizedSubPath,
                    repoDir.toString(),
                    importStats.importedFiles(),
                    importStats.totalBytes(),
                    knowledgeBaseService.size()
            );
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("pull repo failed: " + ex.getMessage(), ex);
        }
    }

    private void syncRepository(String repoUrl, String branch, Path repoDir) throws Exception {
        Path gitDir = repoDir.resolve(".git");
        if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
            runGit(List.of("git", "-C", repoDir.toString(), "fetch", "--all", "--prune"), null);
            runGit(List.of("git", "-C", repoDir.toString(), "checkout", branch), null);
            runGit(List.of("git", "-C", repoDir.toString(), "pull", "--ff-only", "origin", branch), null);
            return;
        }

        if (Files.exists(repoDir)) {
            deleteDirectory(repoDir);
        }
        Files.createDirectories(repoDir.getParent());
        runGit(List.of(
                "git", "clone", "--depth", "1", "--branch", branch, repoUrl, repoDir.toString()
        ), null);
    }

    private Path resolveSourceRoot(Path repoDir, String subPath) {
        if (subPath.isBlank()) {
            return repoDir;
        }
        Path sourceRoot = repoDir.resolve(subPath).normalize();
        if (!sourceRoot.startsWith(repoDir)) {
            throw new IllegalArgumentException("Invalid subPath: outside repository");
        }
        if (!Files.exists(sourceRoot) || !Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("subPath does not exist in repository: " + subPath);
        }
        return sourceRoot;
    }

    private void cleanupOldImportedFiles(Path knowledgeDir, String targetPrefix) throws Exception {
        try (Stream<Path> stream = Files.list(knowledgeDir)) {
            List<Path> oldFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(targetPrefix))
                    .collect(Collectors.toList());
            for (Path path : oldFiles) {
                Files.deleteIfExists(path);
            }
        }
    }

    private ImportStats copyKnowledgeFiles(Path sourceRoot, Path knowledgeDir, String targetPrefix) throws Exception {
        List<Path> sourceFiles = listKnowledgeFiles(sourceRoot);
        if (sourceFiles.isEmpty()) {
            throw new IllegalArgumentException("No .md/.txt files found in repository.");
        }

        int imported = 0;
        long totalBytes = 0L;
        for (Path sourceFile : sourceFiles) {
            if (imported >= MAX_IMPORT_FILES) {
                break;
            }
            long fileBytes = Files.size(sourceFile);
            if (fileBytes <= 0 || fileBytes > MAX_FILE_BYTES) {
                continue;
            }

            String relative = sourceRoot.relativize(sourceFile).toString().replace('\\', '/');
            String targetName = targetPrefix + safeFileName(relative);
            Path targetFile = knowledgeDir.resolve(targetName).normalize();
            if (!targetFile.startsWith(knowledgeDir)) {
                continue;
            }
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            imported++;
            totalBytes += fileBytes;
        }

        if (imported == 0) {
            throw new IllegalArgumentException("No eligible .md/.txt files imported (size limit 1MB per file).");
        }
        return new ImportStats(imported, totalBytes);
    }

    private List<Path> listKnowledgeFiles(Path sourceRoot) throws Exception {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains(FileSeparator.gitDirMarker()))
                    .filter(this::isSupportedKnowledgeFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        }
    }

    private boolean isSupportedKnowledgeFile(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".txt");
    }

    private void runGit(List<String> command, Path workingDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().putIfAbsent("GCM_INTERACTIVE", "Never");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() < 4000) {
                    out.append(line).append('\n');
                }
            }
        }

        boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("git command timeout (" + GIT_TIMEOUT_SECONDS + "s). Check network/proxy and retry.");
        }

        int exit = process.exitValue();
        if (exit != 0) {
            String message = out.isEmpty() ? String.join(" ", command) : out.toString().trim();
            throw new IllegalStateException("git command failed: " + message);
        }
    }

    private void deleteDirectory(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> all = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            for (Path p : all) {
                Files.deleteIfExists(p);
            }
        }
    }

    private Path resolveKnowledgeDir() {
        String configured = ragProperties.getKnowledgeDir();
        if (configured == null || configured.isBlank()) {
            configured = "./storage/knowledge";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            return "main";
        }
        return branch.trim();
    }

    private String normalizeSubPath(String subPath) {
        if (subPath == null || subPath.isBlank()) {
            return "";
        }
        String normalized = subPath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("subPath must not contain '..'");
        }
        return normalized;
    }

    private String safeFileName(String relativePath) {
        String raw = relativePath.replace('/', '_');
        String safe = SAFE_SEGMENT.matcher(raw).replaceAll("_");
        if (safe.length() <= 140) {
            return safe;
        }
        String head = safe.substring(0, 100);
        return head + "_" + shortHash(relativePath) + extensionOf(relativePath);
    }

    private String extensionOf(String path) {
        int idx = path.lastIndexOf('.');
        if (idx < 0 || idx == path.length() - 1) {
            return "";
        }
        return path.substring(idx);
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception ex) {
            throw new IllegalStateException("hash failed", ex);
        }
    }

    public record PullResult(String repoUrl,
                             String branch,
                             String subPath,
                             String cacheDir,
                             int importedFiles,
                             long importedBytes,
                             int chunks) {
    }

    private record ImportStats(int importedFiles, long totalBytes) {
    }

    private static final class FileSeparator {
        private FileSeparator() {
        }

        private static String gitDirMarker() {
            return ".git" + java.io.File.separator;
        }
    }
}
