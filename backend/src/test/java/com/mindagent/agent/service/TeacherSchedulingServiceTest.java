package com.mindagent.agent.service;

import com.mindagent.agent.dto.appointment.TeacherSlotRequest;
import com.mindagent.agent.dto.appointment.TeacherSlotResponse;
import com.mindagent.agent.entity.TeacherAvailableSlot;
import com.mindagent.agent.entity.TeacherProfile;
import com.mindagent.agent.repository.AppUserRepository;
import com.mindagent.agent.repository.TeacherAvailableSlotRepository;
import com.mindagent.agent.repository.TeacherProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherSchedulingServiceTest {

    @Mock
    private TeacherProfileRepository teacherProfileRepository;

    @Mock
    private TeacherAvailableSlotRepository teacherAvailableSlotRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private TeacherSchedulingService teacherSchedulingService;

    @Test
    void shouldSplitRangeIntoHourlySlotsWhenCreating() {
        Long teacherUserId = 10L;
        TeacherProfile profile = new TeacherProfile();
        profile.setUserId(teacherUserId);
        profile.setDisplayName("张老师");
        when(teacherProfileRepository.findByUserId(teacherUserId)).thenReturn(Optional.of(profile));
        when(teacherAvailableSlotRepository.findOverlappingSlots(eq(teacherUserId), any(), any())).thenReturn(List.of());
        when(teacherAvailableSlotRepository.saveAll(any())).thenAnswer(invocation -> {
            List<TeacherAvailableSlot> slots = invocation.getArgument(0);
            for (int i = 0; i < slots.size(); i++) {
                setId(slots.get(i), (long) (i + 1));
            }
            return slots;
        });

        List<TeacherSlotResponse> created = teacherSchedulingService.createSlot(teacherUserId, new TeacherSlotRequest(
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 1, 12, 0),
                "心理咨询室 A",
                "值班",
                true
        ));

        assertEquals(3, created.size());
        assertEquals(LocalDateTime.of(2026, 5, 1, 9, 0), created.get(0).startTime());
        assertEquals(LocalDateTime.of(2026, 5, 1, 10, 0), created.get(0).endTime());
        assertEquals(LocalDateTime.of(2026, 5, 1, 11, 0), created.get(2).startTime());
        assertEquals(LocalDateTime.of(2026, 5, 1, 12, 0), created.get(2).endTime());

        ArgumentCaptor<List<TeacherAvailableSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(teacherAvailableSlotRepository).saveAll(captor.capture());
        assertEquals(3, captor.getValue().size());
    }

    @Test
    void shouldRejectNonWholeHourStartOrEnd() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> teacherSchedulingService.createSlot(
                10L,
                new TeacherSlotRequest(
                        LocalDateTime.of(2026, 5, 1, 9, 30),
                        LocalDateTime.of(2026, 5, 1, 11, 0),
                        null,
                        null,
                        true
                )
        ));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void shouldRejectNonIntegerHourDuration() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> teacherSchedulingService.createSlot(
                10L,
                new TeacherSlotRequest(
                        LocalDateTime.of(2026, 5, 1, 9, 0),
                        LocalDateTime.of(2026, 5, 1, 10, 30),
                        null,
                        null,
                        true
                )
        ));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void shouldRejectWholeRangeWhenAnyOverlapExists() {
        TeacherAvailableSlot conflict = new TeacherAvailableSlot();
        conflict.setTeacherUserId(10L);
        conflict.setStartTime(LocalDateTime.of(2026, 5, 1, 10, 0));
        conflict.setEndTime(LocalDateTime.of(2026, 5, 1, 11, 0));
        when(teacherAvailableSlotRepository.findOverlappingSlots(eq(10L), any(), any())).thenReturn(List.of(conflict));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> teacherSchedulingService.createSlot(
                10L,
                new TeacherSlotRequest(
                        LocalDateTime.of(2026, 5, 1, 9, 0),
                        LocalDateTime.of(2026, 5, 1, 12, 0),
                        null,
                        null,
                        true
                )
        ));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(teacherAvailableSlotRepository, never()).saveAll(any());
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private void setId(TeacherAvailableSlot slot, Long id) {
        try {
            var field = TeacherAvailableSlot.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(slot, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
