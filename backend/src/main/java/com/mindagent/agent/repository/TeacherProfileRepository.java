package com.mindagent.agent.repository;

import com.mindagent.agent.entity.TeacherProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {
    Optional<TeacherProfile> findByUserId(Long userId);
}
