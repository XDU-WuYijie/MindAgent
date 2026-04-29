package com.mindagent.agent.dto.appointment;

import java.time.LocalDateTime;

public record TeacherSlotResponse(
        Long id,
        Long teacherUserId,
        String teacherDisplayName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        String location,
        String notes,
        LocalDateTime updatedAt
) {
}
