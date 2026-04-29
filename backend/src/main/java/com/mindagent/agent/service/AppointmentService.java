package com.mindagent.agent.service;

import com.mindagent.agent.dto.appointment.AppointmentResponse;
import com.mindagent.agent.dto.appointment.TeacherSlotResponse;
import com.mindagent.agent.entity.AppUser;
import com.mindagent.agent.entity.AppointmentLog;
import com.mindagent.agent.entity.CounselingAppointment;
import com.mindagent.agent.entity.TeacherAvailableSlot;
import com.mindagent.agent.entity.TeacherProfile;
import com.mindagent.agent.repository.AppUserRepository;
import com.mindagent.agent.repository.AppointmentLogRepository;
import com.mindagent.agent.repository.CounselingAppointmentRepository;
import com.mindagent.agent.repository.TeacherAvailableSlotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AppointmentService {

    private final TeacherAvailableSlotRepository slotRepository;
    private final CounselingAppointmentRepository appointmentRepository;
    private final AppointmentLogRepository appointmentLogRepository;
    private final TeacherSchedulingService teacherSchedulingService;
    private final AppUserRepository appUserRepository;

    public AppointmentService(TeacherAvailableSlotRepository slotRepository,
                              CounselingAppointmentRepository appointmentRepository,
                              AppointmentLogRepository appointmentLogRepository,
                              TeacherSchedulingService teacherSchedulingService,
                              AppUserRepository appUserRepository) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentLogRepository = appointmentLogRepository;
        this.teacherSchedulingService = teacherSchedulingService;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<TeacherSlotResponse> listAvailableSlots() {
        return listAvailableSlots(null, null);
    }

    @Transactional(readOnly = true)
    public List<TeacherSlotResponse> listAvailableSlots(LocalDate date, String teacherName) {
        String teacherNameFilter = teacherName == null ? null : teacherName.trim().toLowerCase(Locale.ROOT);
        return slotRepository.findAllByStatusAndStartTimeAfterOrderByStartTimeAsc("AVAILABLE", LocalDateTime.now()).stream()
                .map(slot -> {
                    TeacherProfile profile = teacherSchedulingService.findProfileByUserId(slot.getTeacherUserId());
                    return new TeacherSlotResponse(
                            slot.getId(),
                            slot.getTeacherUserId(),
                            profile == null ? "" : profile.getDisplayName(),
                            slot.getStartTime(),
                            slot.getEndTime(),
                            slot.getStatus(),
                            slot.getLocation(),
                            slot.getNotes(),
                            slot.getUpdatedAt()
                    );
                })
                .filter(slot -> date == null || date.equals(slot.startTime().toLocalDate()))
                .filter(slot -> teacherNameFilter == null
                        || teacherNameFilter.isBlank()
                        || slot.teacherDisplayName().toLowerCase(Locale.ROOT).contains(teacherNameFilter))
                .toList();
    }

    @Transactional
    public AppointmentResponse createAppointment(Long studentUserId, Long slotId, String note) {
        TeacherAvailableSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        int updated = slotRepository.updateStatusIfCurrent(slotId, "AVAILABLE", "BOOKED", LocalDateTime.now());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot has been booked");
        }
        CounselingAppointment appointment = new CounselingAppointment();
        appointment.setSlotId(slotId);
        appointment.setTeacherUserId(slot.getTeacherUserId());
        appointment.setStudentUserId(studentUserId);
        appointment.setStatus("CONFIRMED");
        appointment.setStudentNote(trimToNull(note));
        appointment.setUpdatedAt(LocalDateTime.now());
        CounselingAppointment saved = appointmentRepository.save(appointment);
        writeLog(saved.getId(), studentUserId, "CREATE", trimToNull(note));
        TeacherAvailableSlot currentSlot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        return toAppointmentResponse(saved, currentSlot);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listMyAppointments(Long studentUserId) {
        return appointmentRepository.findAllByStudentUserIdOrderByCreatedAtDesc(studentUserId).stream()
                .map(this::toAppointmentResponse)
                .toList();
    }

    @Transactional
    public AppointmentResponse cancelMyAppointment(Long studentUserId, Long appointmentId, String reason) {
        CounselingAppointment appointment = appointmentRepository.findByIdAndStudentUserId(appointmentId, studentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        return cancelAppointmentInternal(appointment, studentUserId, reason);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listTeacherAppointments(Long teacherUserId) {
        return appointmentRepository.findAllByTeacherUserIdOrderByCreatedAtDesc(teacherUserId).stream()
                .map(this::toAppointmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getTeacherAppointment(Long teacherUserId, Long appointmentId) {
        CounselingAppointment appointment = appointmentRepository.findByIdAndTeacherUserId(appointmentId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        return toAppointmentResponse(appointment);
    }

    @Transactional
    public AppointmentResponse cancelTeacherAppointment(Long teacherUserId, Long appointmentId, String reason) {
        CounselingAppointment appointment = appointmentRepository.findByIdAndTeacherUserId(appointmentId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        return cancelAppointmentInternal(appointment, teacherUserId, reason);
    }

    @Transactional
    public AppointmentResponse completeTeacherAppointment(Long teacherUserId, Long appointmentId) {
        CounselingAppointment appointment = appointmentRepository.findByIdAndTeacherUserId(appointmentId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (!"CONFIRMED".equalsIgnoreCase(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed appointment can be completed");
        }
        appointment.setStatus("COMPLETED");
        appointment.setCompletedAt(LocalDateTime.now());
        appointment.setUpdatedAt(LocalDateTime.now());
        CounselingAppointment saved = appointmentRepository.save(appointment);
        writeLog(saved.getId(), teacherUserId, "COMPLETE", null);
        return toAppointmentResponse(saved);
    }

    private AppointmentResponse cancelAppointmentInternal(CounselingAppointment appointment, Long actorUserId, String reason) {
        if (!"CONFIRMED".equalsIgnoreCase(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed appointment can be cancelled");
        }
        appointment.setStatus("CANCELLED");
        appointment.setCancelReason(trimToNull(reason));
        appointment.setUpdatedAt(LocalDateTime.now());
        CounselingAppointment saved = appointmentRepository.save(appointment);
        slotRepository.updateStatusIfCurrent(saved.getSlotId(), "BOOKED", "AVAILABLE", LocalDateTime.now());
        writeLog(saved.getId(), actorUserId, "CANCEL", trimToNull(reason));
        return toAppointmentResponse(saved);
    }

    private void writeLog(Long appointmentId, Long actorUserId, String action, String remark) {
        AppointmentLog log = new AppointmentLog();
        log.setAppointmentId(appointmentId);
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setRemark(remark);
        appointmentLogRepository.save(log);
    }

    private AppointmentResponse toAppointmentResponse(CounselingAppointment appointment) {
        TeacherAvailableSlot slot = slotRepository.findById(appointment.getSlotId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        return toAppointmentResponse(appointment, slot);
    }

    private AppointmentResponse toAppointmentResponse(CounselingAppointment appointment, TeacherAvailableSlot slot) {
        TeacherProfile profile = teacherSchedulingService.findProfileByUserId(appointment.getTeacherUserId());
        AppUser student = appUserRepository.findById(appointment.getStudentUserId()).orElse(null);
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getSlotId(),
                appointment.getTeacherUserId(),
                profile == null ? "" : profile.getDisplayName(),
                appointment.getStudentUserId(),
                student == null ? "" : student.getUsername(),
                appointment.getStatus(),
                appointment.getStudentNote(),
                appointment.getCancelReason(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getLocation(),
                appointment.getCompletedAt(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
