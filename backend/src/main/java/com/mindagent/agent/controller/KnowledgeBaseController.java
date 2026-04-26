package com.mindagent.agent.controller;

import com.mindagent.agent.dto.KbPullRepoRequest;
import com.mindagent.agent.service.KnowledgeBaseService;
import com.mindagent.agent.service.RepoKnowledgeIngestService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final RepoKnowledgeIngestService repoKnowledgeIngestService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   RepoKnowledgeIngestService repoKnowledgeIngestService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.repoKnowledgeIngestService = repoKnowledgeIngestService;
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        knowledgeBaseService.reload();
        return Map.of(
                "ok", true,
                "chunks", knowledgeBaseService.size()
        );
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> upload(@RequestPart("file") FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    try {
                        byte[] content = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(content);
                        KnowledgeBaseService.ImportResult result =
                                knowledgeBaseService.importKnowledgeFile(filePart.filename(), content);
                        return Map.<String, Object>of(
                                "ok", true,
                                "file", result.fileName(),
                                "bytes", result.bytes(),
                                "chunks", result.chunks()
                        );
                    } catch (IllegalArgumentException ex) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                });
    }

    @PostMapping("/pull-repo")
    public Mono<ResponseEntity<Map<String, Object>>> pullRepo(@Valid @RequestBody KbPullRepoRequest request) {
        return Mono.fromCallable(() -> repoKnowledgeIngestService.pullRepo(
                        request.getRepoUrl(),
                        request.getBranch(),
                        request.getSubPath()
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> ResponseEntity.ok(Map.<String, Object>of(
                        "ok", true,
                        "repoUrl", result.repoUrl(),
                        "branch", result.branch(),
                        "subPath", result.subPath(),
                        "cacheDir", result.cacheDir(),
                        "importedFiles", result.importedFiles(),
                        "importedBytes", result.importedBytes(),
                        "chunks", result.chunks()
                )))
                .onErrorResume(ex -> Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "ok", false,
                        "error", ex.getMessage() == null ? "pull repo failed" : ex.getMessage()
                ))));
    }
}
