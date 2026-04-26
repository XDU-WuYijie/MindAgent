package com.mindagent.agent.repository;

import com.mindagent.agent.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    List<ChatSession> findAllByUserIdOrderByLastMessageAtDescCreatedAtDesc(Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
