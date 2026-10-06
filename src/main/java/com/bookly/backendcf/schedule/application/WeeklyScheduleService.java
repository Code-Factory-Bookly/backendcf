package com.bookly.backendcf.schedule.application;

import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.schedule.domain.model.WeeklySlot;
import com.bookly.backendcf.schedule.infrastructure.persistence.WeeklySlotRepository;
import com.bookly.backendcf.schedule.presentation.dto.ScheduleRequest;
import com.bookly.backendcf.schedule.presentation.dto.ScheduleResponse;
import com.bookly.backendcf.schedule.presentation.dto.SlotRequest;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyScheduleService {

    private final WeeklySlotRepository slotRepository;
    private final ProfessionalRepository professionalRepository;

    public WeeklyScheduleService(WeeklySlotRepository slotRepository, ProfessionalRepository professionalRepository) {
        this.slotRepository = slotRepository;
        this.professionalRepository = professionalRepository;
    }

    @Transactional(readOnly = true)
    public ScheduleResponse find(UUID professionalId) {
        requireProfessional(professionalId);
        return ScheduleResponse.from(professionalId,
                slotRepository.findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(professionalId));
    }

    @Transactional
    public ScheduleResponse replace(UUID professionalId, ScheduleRequest request) {
        requireProfessional(professionalId);
        validate(request.slots());

        slotRepository.deleteAll(slotRepository.findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(professionalId));
        slotRepository.flush();

        List<WeeklySlot> saved = slotRepository.saveAllAndFlush(request.slots().stream()
                .map(slot -> new WeeklySlot(professionalId, slot.dayOfWeek(), slot.startTime(), slot.endTime()))
                .toList());
        return ScheduleResponse.from(professionalId, saved);
    }

    private void requireProfessional(UUID professionalId) {
        if (!professionalRepository.existsById(professionalId)) {
            throw new ProfessionalNotFoundException(professionalId);
        }
    }

    private void validate(List<SlotRequest> slots) {
        Map<String, String> conflicts = new LinkedHashMap<>();
        for (SlotRequest slot : slots) {
            if (!slot.endTime().isAfter(slot.startTime())) {
                conflicts.put(label(slot), "La hora de fin debe ser posterior a la de inicio");
            }
        }
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(conflicts);
        }

        for (DayOfWeek day : DayOfWeek.values()) {
            List<SlotRequest> daySlots = slots.stream()
                    .filter(slot -> slot.dayOfWeek() == day)
                    .sorted(Comparator.comparing(SlotRequest::startTime))
                    .toList();
            for (int i = 1; i < daySlots.size(); i++) {
                SlotRequest previous = daySlots.get(i - 1);
                SlotRequest current = daySlots.get(i);
                if (current.startTime().isBefore(previous.endTime())) {
                    conflicts.put(label(current), "Se superpone con " + label(previous));
                }
            }
        }
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(conflicts);
        }
    }

    private String label(SlotRequest slot) {
        return slot.dayOfWeek() + " " + slot.startTime() + "-" + slot.endTime();
    }
}
