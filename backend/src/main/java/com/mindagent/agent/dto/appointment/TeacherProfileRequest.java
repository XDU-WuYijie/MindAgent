package com.mindagent.agent.dto.appointment;

public record TeacherProfileRequest(
        String displayName,
        String title,
        String officeLocation,
        String contactPhone,
        String bio
) {
}
