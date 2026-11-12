package com.bookly.backendcf.report.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/** Franja semanal de atención de un profesional; define su capacidad. */
public record WeeklyWindow(UUID professionalId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
