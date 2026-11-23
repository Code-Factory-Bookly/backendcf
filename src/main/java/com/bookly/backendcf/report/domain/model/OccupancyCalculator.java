package com.bookly.backendcf.report.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
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
                if (window.dayOfWeek().equals(date.getDayOfWeek())) {
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
                if (window.dayOfWeek().equals(date.getDayOfWeek())) {
                    result.merge(date.getDayOfWeek(), windowMinutes(window), Long::sum);
                }
            }
        }
        return result;
    }

    /**
     * #213 y #217: minutos de capacidad de cada profesional. Por cada día del rango toma la unión de sus
     * franjas (dos franjas solapadas no se suman dos veces) y le descuenta sus bloqueos de agenda.
     */
    public static Map<UUID, Long> availableByProfessional(
            List<WeeklyWindow> windows, List<BlockedPeriod> blocks, LocalDate from, LocalDate to) {
        Map<UUID, Long> result = new HashMap<>();
        forEachProfessionalDay(windows, blocks, from, to,
                (professionalId, date, minutes) -> result.merge(professionalId, minutes, Long::sum));
        return result;
    }

    /** #213 y #217: igual que {@link #availableByProfessional}, agrupado por día de la semana. Siempre trae los 7 días. */
    public static Map<DayOfWeek, Long> availableByDay(
            List<WeeklyWindow> windows, List<BlockedPeriod> blocks, LocalDate from, LocalDate to) {
        Map<DayOfWeek, Long> result = emptyDays();
        forEachProfessionalDay(windows, blocks, from, to,
                (professionalId, date, minutes) -> result.merge(date.getDayOfWeek(), minutes, Long::sum));
        return result;
    }

    /**
     * #215: porcentaje real, sin tope: si se reservó más de la capacidad, el valor pasa de 100 y eso
     * delata el problema en vez de ocultarlo. Sin capacidad y sin reservas es 0; sin capacidad pero con
     * reservas no hay porcentaje que calcular y se devuelve {@code null}.
     */
    public static Double exactPercent(long bookedMinutes, long capacityMinutes) {
        if (capacityMinutes <= 0) {
            return bookedMinutes > 0 ? null : 0.0;
        }
        return Math.round(bookedMinutes * 10000.0 / capacityMinutes) / 100.0;
    }

    @FunctionalInterface
    private interface DayCapacity {
        void accept(UUID professionalId, LocalDate date, long minutes);
    }

    private static void forEachProfessionalDay(
            List<WeeklyWindow> windows, List<BlockedPeriod> blocks, LocalDate from, LocalDate to, DayCapacity sink) {
        Map<UUID, List<WeeklyWindow>> windowsByProfessional = new HashMap<>();
        windows.forEach(w -> windowsByProfessional.computeIfAbsent(w.professionalId(), k -> new ArrayList<>()).add(w));
        Map<UUID, List<BlockedPeriod>> blocksByProfessional = new HashMap<>();
        blocks.forEach(b -> blocksByProfessional.computeIfAbsent(b.professionalId(), k -> new ArrayList<>()).add(b));

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (Map.Entry<UUID, List<WeeklyWindow>> entry : windowsByProfessional.entrySet()) {
                LocalDate day = date;
                List<int[]> union = union(entry.getValue().stream()
                        .filter(w -> w.dayOfWeek().equals(day.getDayOfWeek()))
                        .map(w -> new int[] {minuteOfDay(w.startTime()), minuteOfDay(w.endTime())})
                        .toList());
                if (union.isEmpty()) {
                    continue;
                }
                long minutes = union.stream().mapToLong(i -> i[1] - i[0]).sum()
                        - blockedMinutes(union, blocksByProfessional.getOrDefault(entry.getKey(), List.of()), day);
                sink.accept(entry.getKey(), day, Math.max(0, minutes));
            }
        }
    }

    private static List<int[]> union(List<int[]> intervals) {
        List<int[]> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparingInt(i -> i[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] interval : sorted) {
            int[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && interval[0] <= last[1]) {
                last[1] = Math.max(last[1], interval[1]);
            } else {
                merged.add(new int[] {interval[0], interval[1]});
            }
        }
        return merged;
    }

    /** Minutos del día {@code date} en que la unión de franjas y los bloqueos coinciden. */
    private static long blockedMinutes(List<int[]> union, List<BlockedPeriod> blocks, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        long total = 0;
        for (BlockedPeriod block : blocks) {
            if (!block.startTime().isBefore(dayEnd) || !block.endTime().isAfter(dayStart)) {
                continue;
            }
            int from = block.startTime().isAfter(dayStart) ? minuteOfDay(block.startTime().toLocalTime()) : 0;
            int to = block.endTime().isBefore(dayEnd) ? minuteOfDay(block.endTime().toLocalTime()) : 24 * 60;
            for (int[] interval : union) {
                total += Math.max(0, Math.min(interval[1], to) - Math.max(interval[0], from));
            }
        }
        return total;
    }

    private static int minuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
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

    /**
     * Minutos de una franja semanal.
     *
     * <p>Las franjas son horas locales de la plataforma, sin zona. Para calcular la duracion
     * sobre instantes reales (y no sobre naive times) se anclan a la zona horaria por defecto
     * del servidor. La franja de una semana no cruza un cambio de horario, de modo que la
     * diferencia en minutos es la misma que la de las horas locales.
     */
    private static long windowMinutes(WeeklyWindow window) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate referenceDate = LocalDate.of(2000, 1, 1);
        return Duration.between(
                referenceDate.atTime(window.startTime()).atZone(zone).toInstant(),
                referenceDate.atTime(window.endTime()).atZone(zone).toInstant()
        ).toMinutes();
    }

    /**
     * Minutos de una reserva.
     *
     * <p>Misma consideracion que en {@link #windowMinutes(WeeklyWindow)}: las horas de la reserva
     * son locales de la plataforma, y se calculan sobre instantes con zona para no depender de
     * tiempos naive.
     */
    private static long bookingMinutes(BookedPeriod booking) {
        ZoneId zone = ZoneId.systemDefault();
        return Duration.between(
                booking.startTime().atZone(zone).toInstant(),
                booking.endTime().atZone(zone).toInstant()
        ).toMinutes();
    }

    private static Map<DayOfWeek, Long> emptyDays() {
        Map<DayOfWeek, Long> days = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            days.put(day, 0L);
        }
        return days;
    }
}