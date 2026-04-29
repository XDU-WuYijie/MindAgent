package com.mindagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindagent.agent.dto.appointment.AvailableTeacherSlotItemResponse;
import com.mindagent.agent.dto.appointment.AvailableTeacherSlotsResponse;
import com.mindagent.agent.dto.appointment.AppointmentResponse;
import com.mindagent.agent.dto.appointment.TeacherSlotResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AppointmentToolService {

    private final AppointmentService appointmentService;
    private final ObjectMapper objectMapper;

    public AppointmentToolService(AppointmentService appointmentService,
                                  ObjectMapper objectMapper) {
        this.appointmentService = appointmentService;
        this.objectMapper = objectMapper;
    }

    public String queryAvailableSlots(Long userId, String argumentsJson) {
        Map<String, Object> args = parse(argumentsJson);
        LocalDate date = toLocalDate(args.get("date"));
        String teacherName = stringValue(args.get("teacherName"));
        List<TeacherSlotResponse> slots = appointmentService.listAvailableSlots(date, teacherName);
        List<AvailableTeacherSlotsResponse> teachers = slots.stream()
                .collect(Collectors.groupingBy(
                        TeacherSlotResponse::teacherUserId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(group -> new AvailableTeacherSlotsResponse(
                        group.getFirst().teacherUserId(),
                        group.getFirst().teacherDisplayName(),
                        group.getFirst().startTime(),
                        group.stream()
                                .map(slot -> new AvailableTeacherSlotItemResponse(
                                        slot.id(),
                                        slot.startTime(),
                                        slot.endTime(),
                                        slot.location(),
                                        slot.notes()
                                ))
                                .toList()
                ))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", date);
        payload.put("teacherName", teacherName);
        payload.put("teacherCount", teachers.size());
        payload.put("slotCount", slots.size());
        payload.put("teachers", teachers);
        return toJson(payload);
    }

    public String createAppointment(Long userId, String argumentsJson) {
        Map<String, Object> args = parse(argumentsJson);
        Long slotId = toLong(args.get("slotId"));
        String note = stringValue(args.get("note"));
        AppointmentResponse response = appointmentService.createAppointment(userId, slotId, note);
        return toJson(response);
    }

    public String queryMyAppointments(Long userId, String argumentsJson) {
        List<AppointmentResponse> items = appointmentService.listMyAppointments(userId);
        return toJson(Map.of("count", items.size(), "items", items));
    }

    public String cancelAppointment(Long userId, String argumentsJson) {
        Map<String, Object> args = parse(argumentsJson);
        Long appointmentId = toLong(args.get("appointmentId"));
        String reason = stringValue(args.get("reason"));
        AppointmentResponse response = appointmentService.cancelMyAppointment(userId, appointmentId, reason);
        return toJson(response);
    }

    private Map<String, Object> parse(String argumentsJson) {
        try {
            if (argumentsJson == null || argumentsJson.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(argumentsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid tool arguments: " + ex.getMessage(), ex);
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        throw new IllegalArgumentException("Missing required numeric argument");
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : LocalDate.parse(text);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize tool result", ex);
        }
    }
}
