package com.mindagent.agent.repository;

import com.mindagent.agent.entity.ChatMemoryCompressLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMemoryCompressLogRepository extends JpaRepository<ChatMemoryCompressLog, Long> {

    void deleteBySessionIdAndUserId(Long sessionId, Long userId);
}
