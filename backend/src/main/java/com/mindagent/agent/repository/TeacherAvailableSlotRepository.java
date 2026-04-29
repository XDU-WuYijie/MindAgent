package com.mindagent.agent.repository;

import com.mindagent.agent.entity.TeacherAvailableSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TeacherAvailableSlotRepository extends JpaRepository<TeacherAvailableSlot, Long> {
    List<TeacherAvailableSlot> findAllByTeacherUserIdOrderByStartTimeAsc(Long teacherUserId);
    List<TeacherAvailableSlot> findAllByStatusAndStartTimeAfterOrderByStartTimeAsc(String status, LocalDateTime startTime);
    Optional<TeacherAvailableSlot> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    @Query("""
            select s from TeacherAvailableSlot s
            where s.teacherUserId = :teacherUserId
              and s.startTime < :endTime
              and s.endTime > :startTime
            order by s.startTime asc
            """)
    List<TeacherAvailableSlot> findOverlappingSlots(@Param("teacherUserId") Long teacherUserId,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);

    @Query("""
            select s from TeacherAvailableSlot s
            where s.teacherUserId = :teacherUserId
              and s.id <> :slotId
              and s.startTime < :endTime
              and s.endTime > :startTime
            order by s.startTime asc
            """)
    List<TeacherAvailableSlot> findOverlappingSlotsExcludingId(@Param("teacherUserId") Long teacherUserId,
                                                               @Param("slotId") Long slotId,
                                                               @Param("startTime") LocalDateTime startTime,
                                                               @Param("endTime") LocalDateTime endTime);

    @Modifying
    @Query("update TeacherAvailableSlot s set s.status = :newStatus, s.updatedAt = :updatedAt where s.id = :id and s.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("expectedStatus") String expectedStatus,
                              @Param("newStatus") String newStatus,
                              @Param("updatedAt") LocalDateTime updatedAt);
}
