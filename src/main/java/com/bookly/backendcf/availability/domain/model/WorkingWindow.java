package com.bookly.backendcf.availability.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Franja de atención semanal de un profesional (por ejemplo, lunes de 08:00 a 12:00). */
public record WorkingWindow(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
}