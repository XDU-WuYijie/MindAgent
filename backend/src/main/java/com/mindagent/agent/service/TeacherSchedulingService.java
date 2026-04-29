package com.mindagent.agent.service;

import com.mindagent.agent.dto.appointment.TeacherProfileRequest;
import com.mindagent.agent.dto.appointment.TeacherProfileResponse;
import com.mindagent.agent.dto.appointment.TeacherSlotRequest;
import com.mindagent.agent.dto.appointment.TeacherSlotResponse;
import com.mindagent.agent.entity.AppUser;
import com.mindagent.agent.entity.TeacherAvailableSlot;
import com.mindagent.agent.entity.TeacherProfile;
import com.mindagent.agent.repository.AppUserRepository;
import com.mindagent.agent.repository.TeacherAvailableSlotRepository;
import com.mindagent.agent.repository.TeacherProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class TeacherSchedulingService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherAvailableSlotRepository teacherAvailableSlotRepository;
    private final AppUserRepository appUserRepository;

    public TeacherSchedulingService(TeacherProfileRepository teacherProfileRepository,
                                    TeacherAvailableSlotRepository teacherAvailableSlotRepository,
                                    AppUserRepository appUserRepository) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherAvailableSlotRepository = teacherAvailableSlotRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public TeacherProfileResponse getMyProfile(Long teacherUserId) {
        TeacherProfile profile = teacherProfileRepository.findByUserId(teacherUserId)
                .orElseGet(() -> createDefaultProfile(teacherUserId));
        return toProfileResponse(profile);
    }

    @Transactional
    public TeacherProfileResponse updateMyProfile(Long teacherUserId, TeacherProfileRequest request) {
        TeacherProfile profile = teacherProfileRepository.findByUserId(teacherUserId)
                .orElseGet(() -> createDefaultProfile(teacherUserId));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            profile.setDisplayName(request.displayName().trim());
        }
        profile.setTitle(trimToNull(request.title()));
        profile.setOfficeLocation(trimToNull(request.officeLocation()));
        profile.setContactPhone(trimToNull(request.contactPhone()));
        profile.setBio(trimToNull(request.bio()));
        profile.setUpdatedAt(LocalDateTime.now());
        return toProfileResponse(teacherProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public List<TeacherSlotResponse> listMySlots(Long teacherUserId) {
        return teacherAvailableSlotRepository.findAllByTeacherUserIdOrderByStartTimeAsc(teacherUserId).stream()
                .map(this::toSlotResponse)
                .toList();
    }

    @Transactional
    public List<TeacherSlotResponse> createSlot(Long teacherUserId, TeacherSlotRequest request) {
        List<LocalDateTime[]> ranges = splitIntoHourlyRanges(request.startTime(), request.endTime());
        List<TeacherAvailableSlot> conflicts = teacherAvailableSlotRepository.findOverlappingSlots(
                teacherUserId,
                request.startTime(),
                request.endTime()
        );
        if (!conflicts.isEmpty()) {
            TeacherAvailableSlot conflict = conflicts.getFirst();
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Schedule conflict: " + conflict.getStartTime() + " to " + conflict.getEndTime()
            );
        }
        LocalDateTime now = LocalDateTime.now();
        String status = Boolean.FALSE.equals(request.open()) ? "CLOSED" : "AVAILABLE";
        List<TeacherAvailableSlot> slots = new ArrayList<>();
        for (LocalDateTime[] range : ranges) {
            TeacherAvailableSlot slot = new TeacherAvailableSlot();
            slot.setTeacherUserId(teacherUserId);
            slot.setStartTime(range[0]);
            slot.setEndTime(range[1]);
            slot.setLocation(trimToNull(request.location()));
            slot.setNotes(trimToNull(request.notes()));
            slot.setStatus(status);
            slot.setUpdatedAt(now);
            slots.add(slot);
        }
        return teacherAvailableSlotRepository.saveAll(slots).stream()
                .map(this::toSlotResponse)
                .toList();
    }

    @Transactional
    public TeacherSlotResponse updateSlot(Long teacherUserId, Long slotId, TeacherSlotRequest request) {
        validateSingleSlot(request.startTime(), request.endTime());
        TeacherAvailableSlot slot = requireOwnedSlot(teacherUserId, slotId);
        if ("BOOKED".equalsIgnoreCase(slot.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booked slot cannot be edited");
        }
        List<TeacherAvailableSlot> conflicts = teacherAvailableSlotRepository.findOverlappingSlotsExcludingId(
                teacherUserId,
                slotId,
                request.startTime(),
                request.endTime()
        );
        if (!conflicts.isEmpty()) {
            TeacherAvailableSlot conflict = conflicts.getFirst();
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Schedule conflict: " + conflict.getStartTime() + " to " + conflict.getEndTime()
            );
        }
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setLocation(trimToNull(request.location()));
        slot.setNotes(trimToNull(request.notes()));
        slot.setStatus(Boolean.FALSE.equals(request.open()) ? "CLOSED" : "AVAILABLE");
        slot.setUpdatedAt(LocalDateTime.now());
        return toSlotResponse(teacherAvailableSlotRepository.save(slot));
    }

    @Transactional
    public void deleteSlot(Long teacherUserId, Long slotId) {
        TeacherAvailableSlot slot = requireOwnedSlot(teacherUserId, slotId);
        if ("BOOKED".equalsIgnoreCase(slot.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booked slot cannot be deleted");
        }
        teacherAvailableSlotRepository.delete(slot);
    }

    @Transactional
    public TeacherSlotResponse openSlot(Long teacherUserId, Long slotId) {
        TeacherAvailableSlot slot = requireOwnedSlot(teacherUserId, slotId);
        if ("BOOKED".equalsIgnoreCase(slot.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booked slot cannot be reopened");
        }
        slot.setStatus("AVAILABLE");
        slot.setUpdatedAt(LocalDateTime.now());
        return toSlotResponse(teacherAvailableSlotRepository.save(slot));
    }

    @Transactional
    public TeacherSlotResponse closeSlot(Long teacherUserId, Long slotId) {
        TeacherAvailableSlot slot = requireOwnedSlot(teacherUserId, slotId);
        if ("BOOKED".equalsIgnoreCase(slot.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booked slot cannot be closed");
        }
        slot.setStatus("CLOSED");
        slot.setUpdatedAt(LocalDateTime.now());
        return toSlotResponse(teacherAvailableSlotRepository.save(slot));
    }

    @Transactional(readOnly = true)
    public TeacherAvailableSlot requireOwnedSlot(Long teacherUserId, Long slotId) {
        return teacherAvailableSlotRepository.findByIdAndTeacherUserId(slotId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
    }

    @Transactional(readOnly = true)
    public TeacherProfile findProfileByUserId(Long userId) {
        return teacherProfileRepository.findByUserId(userId).orElse(null);
    }

    List<LocalDateTime[]> splitIntoHourlyRanges(LocalDateTime startTime, LocalDateTime endTime) {
        validateRangeSlot(startTime, endTime);
        List<LocalDateTime[]> ranges = new ArrayList<>();
        LocalDateTime cursor = startTime;
        while (cursor.isBefore(endTime)) {
            LocalDateTime next = cursor.plusHours(1);
            ranges.add(new LocalDateTime[]{cursor, next});
            cursor = next;
        }
        return ranges;
    }

    private void validateSingleSlot(LocalDateTime startTime, LocalDateTime endTime) {
        validateRangeSlot(startTime, endTime);
        if (Duration.between(startTime, endTime).toHours() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each schedule entry must be exactly 1 hour");
        }
    }

    private void validateRangeSlot(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid slot time range");
        }
        if (!isWholeHour(startTime) || !isWholeHour(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time and end time must be on the hour");
        }
        long minutes = Duration.between(startTime, endTime).toMinutes();
        if (minutes % 60 != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Time range must be in whole hours");
        }
    }

    private boolean isWholeHour(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.HOURS).equals(time);
    }

    private TeacherProfile createDefaultProfile(Long teacherUserId) {
        AppUser user = appUserRepository.findById(teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found"));
        TeacherProfile profile = new TeacherProfile();
        profile.setUserId(teacherUserId);
        profile.setDisplayName(user.getUsername());
        return teacherProfileRepository.save(profile);
    }

    private TeacherProfileResponse toProfileResponse(TeacherProfile profile) {
        return new TeacherProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getTitle(),
                profile.getOfficeLocation(),
                profile.getContactPhone(),
                profile.getBio(),
                profile.getUpdatedAt()
        );
    }

    private TeacherSlotResponse toSlotResponse(TeacherAvailableSlot slot) {
        TeacherProfile profile = findProfileByUserId(slot.getTeacherUserId());
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
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
