package com.mindagent.agent.repository;

import com.mindagent.agent.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findAllByDocumentIdOrderByChunkIndexAsc(Long documentId);

    List<KnowledgeChunk> findAllByDocumentIdInOrderByDocumentIdAscChunkIndexAsc(Collection<Long> documentIds);

    void deleteAllByDocumentId(Long documentId);

    void deleteAllByDocumentIdIn(Collection<Long> documentIds);
}
