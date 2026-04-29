package com.mindagent.agent.dto.appointment;

import java.time.LocalDateTime;
import java.util.List;

public record AvailableTeacherSlotsResponse(
        Long teacherUserId,
        String teacherDisplayName,
        LocalDateTime nextAvailableTime,
        List<AvailableTeacherSlotItemResponse> slots
) {
}
