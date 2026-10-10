package com.bookly.backendcf.availability.presentation.dto;

import com.bookly.backendcf.availability.domain.model.AvailableSlot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
        UUID professionalId,
        UUID serviceId,
        int durationMinutes,
        LocalDate from,
        LocalDate to,
        boolean available,
        String message,
        List<Day> days) {

    public record Day(LocalDate date, List<Block> slots) {

        public static Day from(LocalDate date, List<AvailableSlot> slots) {
            return new Day(date, slots.stream().map(Block::from).toList());
        }
    }

    public record Block(LocalDateTime startTime, LocalDateTime endTime) {

        public static Block from(AvailableSlot slot) {
            return new Block(slot.startTime(), slot.endTime());
        }
    }
}