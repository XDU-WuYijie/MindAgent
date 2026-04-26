package com.mindagent.agent.repository;

import com.mindagent.agent.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    List<ChatMessage> findBySessionIdAndUserIdOrderByCreatedAtAscIdAsc(Long sessionId, Long userId);

    List<ChatMessage> findTop100BySessionIdAndUserIdAndCompressedFalseOrderByCreatedAtDescIdDesc(Long sessionId, Long userId);

    Optional<ChatMessage> findTopBySessionIdAndUserIdOrderByCreatedAtDescIdDesc(Long sessionId, Long userId);

    long countBySessionIdAndUserIdAndCompressedFalse(Long sessionId, Long userId);

    @Modifying
    @Query("update ChatMessage m set m.compressed = true where m.id in :ids")
    void markCompressedByIds(Collection<Long> ids);

    void deleteBySessionIdAndUserId(Long sessionId, Long userId);
}
