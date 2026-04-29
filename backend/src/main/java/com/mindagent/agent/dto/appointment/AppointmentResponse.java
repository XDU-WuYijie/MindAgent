package com.mindagent.agent.dto.appointment;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        Long slotId,
        Long teacherUserId,
        String teacherDisplayName,
        Long studentUserId,
        String studentUsername,
        String status,
        String studentNote,
        String cancelReason,
        LocalDateTime slotStartTime,
        LocalDateTime slotEndTime,
        String slotLocation,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
