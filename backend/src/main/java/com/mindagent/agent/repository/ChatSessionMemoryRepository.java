package com.mindagent.agent.repository;

import com.mindagent.agent.entity.ChatSessionMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionMemoryRepository extends JpaRepository<ChatSessionMemory, Long> {

    Optional<ChatSessionMemory> findBySessionIdAndUserId(Long sessionId, Long userId);

    void deleteBySessionIdAndUserId(Long sessionId, Long userId);
}
