package com.mindagent.agent.dto.appointment;

import java.time.LocalDateTime;

public record TeacherProfileResponse(
        Long id,
        Long userId,
        String displayName,
        String title,
        String officeLocation,
        String contactPhone,
        String bio,
        LocalDateTime updatedAt
) {
}
