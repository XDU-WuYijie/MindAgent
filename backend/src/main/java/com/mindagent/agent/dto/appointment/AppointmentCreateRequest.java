package com.mindagent.agent.dto.appointment;

public record AppointmentCreateRequest(
        Long slotId,
        String note
) {
}
