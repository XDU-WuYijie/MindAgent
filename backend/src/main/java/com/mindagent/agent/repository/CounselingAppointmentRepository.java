package com.mindagent.agent.repository;

import com.mindagent.agent.entity.CounselingAppointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CounselingAppointmentRepository extends JpaRepository<CounselingAppointment, Long> {
    List<CounselingAppointment> findAllByStudentUserIdOrderByCreatedAtDesc(Long studentUserId);
    List<CounselingAppointment> findAllByTeacherUserIdOrderByCreatedAtDesc(Long teacherUserId);
    Optional<CounselingAppointment> findByIdAndStudentUserId(Long id, Long studentUserId);
    Optional<CounselingAppointment> findByIdAndTeacherUserId(Long id, Long teacherUserId);
}
