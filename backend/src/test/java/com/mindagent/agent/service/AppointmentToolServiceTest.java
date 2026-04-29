package com.mindagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindagent.agent.dto.appointment.TeacherSlotResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentToolServiceTest {

    @Mock
    private AppointmentService appointmentService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldGroupAvailableSlotsByTeacherAndHonorFilters() throws Exception {
        AppointmentToolService appointmentToolService = new AppointmentToolService(appointmentService, objectMapper);
        when(appointmentService.listAvailableSlots(LocalDate.of(2026, 5, 1), "张老师")).thenReturn(List.of(
                new TeacherSlotResponse(1L, 101L, "张老师", LocalDateTime.of(2026, 5, 1, 9, 0), LocalDateTime.of(2026, 5, 1, 10, 0), "AVAILABLE", "A101", "面对面", LocalDateTime.of(2026, 4, 29, 10, 0)),
                new TeacherSlotResponse(2L, 101L, "张老师", LocalDateTime.of(2026, 5, 1, 10, 0), LocalDateTime.of(2026, 5, 1, 11, 0), "AVAILABLE", "A101", "面对面", LocalDateTime.of(2026, 4, 29, 10, 0))
        ));

        String result = appointmentToolService.queryAvailableSlots(1L, """
                {"date":"2026-05-01","teacherName":"张老师"}
                """);
        JsonNode root = objectMapper.readTree(result);

        assertEquals(1, root.get("teacherCount").asInt());
        assertEquals(2, root.get("slotCount").asInt());
        assertEquals("张老师", root.get("teachers").get(0).get("teacherDisplayName").asText());
        assertEquals(2, root.get("teachers").get(0).get("slots").size());
        assertEquals(1L, root.get("teachers").get(0).get("slots").get(0).get("slotId").asLong());
        verify(appointmentService).listAvailableSlots(LocalDate.of(2026, 5, 1), "张老师");
    }
}
