package com.mindagent.agent.repository;

import com.mindagent.agent.entity.McpDispatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface McpDispatchLogRepository extends JpaRepository<McpDispatchLog, Long> {
    List<McpDispatchLog> findTop50ByOrderByCreatedAtDesc();
}
