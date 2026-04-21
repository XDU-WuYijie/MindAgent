package com.mindbridge.agent.repository;

import com.mindbridge.agent.entity.PsychologicalReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PsychologicalReportRepository extends JpaRepository<PsychologicalReport, Long> {

    List<PsychologicalReport> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
