package com.bookly.backendcf.report.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cálculo de minutos reservados, capacidad y porcentaje de ocupación (HU-15). */
public final class OccupancyCalculator {

    private OccupancyCalculator() {
    }

    /** Minutos de capacidad de cada profesional: sus franjas por cada día del rango. */
    public static Map<UUID, Long> capacityByProfessional(List<WeeklyWindow> windows, LocalDate from, LocalDate to) {
        Map<UUID, Long> result = new HashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (WeeklyWindow window : windows) {
                if (window.dayOfWeek() == date.getDayOfWeek()) {
                    result.merge(window.professionalId(), windowMinutes(window), Long::sum);
                }
            }
        }
        return result;
    }

    /** Minutos de capacidad por día de la semana, sumando todos los profesionales. Siempre trae los 7 días. */
    public static Map<DayOfWeek, Long> capacityByDay(List<WeeklyWindow> windows, LocalDate from, LocalDate to) {
        Map<DayOfWeek, Long> result = emptyDays();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (WeeklyWindow window : windows) {
                if (window.dayOfWeek() == date.getDayOfWeek()) {
                    result.merge(date.getDayOfWeek(), windowMinutes(window), Long::sum);
                }
            }
        }
        return result;
    }

    /** Minutos reservados de cada profesional. */
    public static Map<UUID, Long> bookedByProfessional(List<BookedPeriod> bookings) {
        Map<UUID, Long> result = new HashMap<>();
        for (BookedPeriod booking : bookings) {
            result.merge(booking.professionalId(), bookingMinutes(booking), Long::sum);
        }
        return result;
    }

    /** Minutos reservados por día de la semana (según el día de inicio de la reserva). Siempre trae los 7 días. */
    public static Map<DayOfWeek, Long> bookedByDay(List<BookedPeriod> bookings) {
        Map<DayOfWeek, Long> result = emptyDays();
        for (BookedPeriod booking : bookings) {
            result.merge(booking.startTime().getDayOfWeek(), bookingMinutes(booking), Long::sum);
        }
        return result;
    }

    /** Porcentaje con dos decimales y tope de 100. Si no hay capacidad devuelve 0 (sin dividir por cero). */
    public static double percent(long bookedMinutes, long capacityMinutes) {
        if (capacityMinutes <= 0) {
            return 0.0;
        }
        double percent = Math.round(bookedMinutes * 10000.0 / capacityMinutes) / 100.0;
        return Math.min(100.0, percent);
    }

    private static long windowMinutes(WeeklyWindow window) {
        return Duration.between(window.startTime(), window.endTime()).toMinutes();
    }

    private static long bookingMinutes(BookedPeriod booking) {
        return Duration.between(booking.startTime(), booking.endTime()).toMinutes();
    }

    private static Map<DayOfWeek, Long> emptyDays() {
        Map<DayOfWeek, Long> days = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            days.put(day, 0L);
        }
        return days;
    }
}