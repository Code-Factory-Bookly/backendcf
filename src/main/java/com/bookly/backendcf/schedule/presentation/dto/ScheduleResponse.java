package com.bookly.backendcf.schedule.presentation.dto;

import com.bookly.backendcf.schedule.domain.model.WeeklySlot;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ScheduleResponse(UUID professionalId, List<Slot> slots) {

    public static ScheduleResponse from(UUID professionalId, List<WeeklySlot> slots) {
        return new ScheduleResponse(
                professionalId,
                slots.stream().map(slot -> new Slot(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime())).toList());
    }

    public record Slot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }
}
