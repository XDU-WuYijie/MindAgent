package com.mindagent.agent.repository;

import com.mindagent.agent.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    List<KnowledgeDocument> findAllByStatusOrderByCreatedAtDesc(String status);

    List<KnowledgeDocument> findAllByKnowledgeBaseKeyOrderByCreatedAtDesc(String knowledgeBaseKey);

    List<KnowledgeDocument> findAllByKnowledgeBaseKeyAndStatusOrderByCreatedAtDesc(String knowledgeBaseKey, String status);

    Optional<KnowledgeDocument> findByIdAndStatus(Long id, String status);

    Optional<KnowledgeDocument> findByKnowledgeBaseKeyAndStoredFilename(String knowledgeBaseKey, String storedFilename);
}
