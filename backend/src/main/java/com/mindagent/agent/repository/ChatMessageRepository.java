package com.mindagent.agent.repository;

import com.mindagent.agent.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
