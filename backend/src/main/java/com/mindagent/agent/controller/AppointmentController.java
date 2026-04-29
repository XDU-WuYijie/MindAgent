package com.mindagent.agent.controller;

import com.mindagent.agent.dto.appointment.AppointmentActionRequest;
import com.mindagent.agent.dto.appointment.AppointmentCreateRequest;
import com.mindagent.agent.dto.appointment.AppointmentResponse;
import com.mindagent.agent.dto.appointment.TeacherSlotResponse;
import com.mindagent.agent.service.AppointmentService;
import com.mindagent.agent.service.CurrentUserSupport;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/appointment")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final CurrentUserSupport currentUserSupport;

    public AppointmentController(AppointmentService appointmentService,
                                 CurrentUserSupport currentUserSupport) {
        this.appointmentService = appointmentService;
        this.currentUserSupport = currentUserSupport;
    }

    @GetMapping("/slots/available")
    public List<TeacherSlotResponse> availableSlots() {
        return appointmentService.listAvailableSlots();
    }

    @PostMapping("/create")
    public AppointmentResponse create(@AuthenticationPrincipal Jwt jwt,
                                      @RequestBody AppointmentCreateRequest request) {
        return appointmentService.createAppointment(currentUserSupport.requireUserId(jwt), request.slotId(), request.note());
    }

    @GetMapping("/my")
    public List<AppointmentResponse> myAppointments(@AuthenticationPrincipal Jwt jwt) {
        return appointmentService.listMyAppointments(currentUserSupport.requireUserId(jwt));
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable Long id,
                                      @RequestBody(required = false) AppointmentActionRequest request) {
        return appointmentService.cancelMyAppointment(currentUserSupport.requireUserId(jwt), id, request == null ? null : request.reason());
    }
}
