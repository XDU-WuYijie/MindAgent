package com.mindagent.agent.dto.appointment;

import java.time.LocalDateTime;

public record TeacherSlotRequest(
        LocalDateTime startTime,
        LocalDateTime endTime,
        String location,
        String notes,
        Boolean open
) {
}
