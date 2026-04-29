package com.mindagent.agent.controller;

import com.mindagent.agent.dto.appointment.AppointmentActionRequest;
import com.mindagent.agent.dto.appointment.AppointmentResponse;
import com.mindagent.agent.dto.appointment.TeacherProfileRequest;
import com.mindagent.agent.dto.appointment.TeacherProfileResponse;
import com.mindagent.agent.dto.appointment.TeacherSlotRequest;
import com.mindagent.agent.dto.appointment.TeacherSlotResponse;
import com.mindagent.agent.service.AppointmentService;
import com.mindagent.agent.service.CurrentUserSupport;
import com.mindagent.agent.service.TeacherSchedulingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherSchedulingService teacherSchedulingService;
    private final AppointmentService appointmentService;
    private final CurrentUserSupport currentUserSupport;

    public TeacherController(TeacherSchedulingService teacherSchedulingService,
                             AppointmentService appointmentService,
                             CurrentUserSupport currentUserSupport) {
        this.teacherSchedulingService = teacherSchedulingService;
        this.appointmentService = appointmentService;
        this.currentUserSupport = currentUserSupport;
    }

    @GetMapping("/profile/me")
    public TeacherProfileResponse getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return teacherSchedulingService.getMyProfile(currentUserSupport.requireUserId(jwt));
    }

    @PutMapping("/profile/me")
    public TeacherProfileResponse updateMyProfile(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestBody TeacherProfileRequest request) {
        return teacherSchedulingService.updateMyProfile(currentUserSupport.requireUserId(jwt), request);
    }

    @GetMapping("/slots")
    public List<TeacherSlotResponse> listSlots(@AuthenticationPrincipal Jwt jwt) {
        return teacherSchedulingService.listMySlots(currentUserSupport.requireUserId(jwt));
    }

    @PostMapping("/slots")
    public List<TeacherSlotResponse> createSlot(@AuthenticationPrincipal Jwt jwt,
                                                @RequestBody TeacherSlotRequest request) {
        return teacherSchedulingService.createSlot(currentUserSupport.requireUserId(jwt), request);
    }

    @PutMapping("/slots/{id}")
    public TeacherSlotResponse updateSlot(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable Long id,
                                          @RequestBody TeacherSlotRequest request) {
        return teacherSchedulingService.updateSlot(currentUserSupport.requireUserId(jwt), id, request);
    }

    @DeleteMapping("/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        teacherSchedulingService.deleteSlot(currentUserSupport.requireUserId(jwt), id);
    }

    @PatchMapping("/slots/{id}/open")
    public TeacherSlotResponse openSlot(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return teacherSchedulingService.openSlot(currentUserSupport.requireUserId(jwt), id);
    }

    @PatchMapping("/slots/{id}/close")
    public TeacherSlotResponse closeSlot(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return teacherSchedulingService.closeSlot(currentUserSupport.requireUserId(jwt), id);
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> listAppointments(@AuthenticationPrincipal Jwt jwt) {
        return appointmentService.listTeacherAppointments(currentUserSupport.requireUserId(jwt));
    }

    @GetMapping("/appointments/{id}")
    public AppointmentResponse getAppointment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return appointmentService.getTeacherAppointment(currentUserSupport.requireUserId(jwt), id);
    }

    @PatchMapping("/appointments/{id}/cancel")
    public AppointmentResponse cancelAppointment(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable Long id,
                                                 @RequestBody(required = false) AppointmentActionRequest request) {
        return appointmentService.cancelTeacherAppointment(currentUserSupport.requireUserId(jwt), id, request == null ? null : request.reason());
    }

    @PatchMapping("/appointments/{id}/complete")
    public AppointmentResponse completeAppointment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return appointmentService.completeTeacherAppointment(currentUserSupport.requireUserId(jwt), id);
    }
}
