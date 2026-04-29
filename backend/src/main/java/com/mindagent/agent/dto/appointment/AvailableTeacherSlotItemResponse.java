package com.mindagent.agent.dto.appointment;

import java.time.LocalDateTime;

public record AvailableTeacherSlotItemResponse(
        Long slotId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String location,
        String notes
) {
}
