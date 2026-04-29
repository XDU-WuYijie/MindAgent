package com.mindagent.agent.repository;

import com.mindagent.agent.entity.AppointmentLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentLogRepository extends JpaRepository<AppointmentLog, Long> {
}
