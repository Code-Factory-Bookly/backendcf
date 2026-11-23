package com.bookly.backendcf.availability.domain.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    public static List<AvailableSlot> freeSlots(
            LocalDate date,
            List<WorkingWindow> windows,
            Duration duration,
            List<BusyPeriod> busyPeriods,
            LocalDateTime now) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("La duración del servicio debe ser positiva");
        }

        List<WorkingWindow> dayWindows = mergeOverlapping(windows.stream()
                .filter(window -> date.getDayOfWeek().equals(window.dayOfWeek()))
                .sorted(Comparator.comparing(WorkingWindow::startTime))
                .toList());

        List<AvailableSlot> free = new ArrayList<>();
        for (WorkingWindow window : dayWindows) {
            LocalDateTime cursor = date.atTime(window.startTime());
            LocalDateTime windowEnd = date.atTime(window.endTime());
            while (!cursor.plus(duration).isAfter(windowEnd)) {
                LocalDateTime blockEnd = cursor.plus(duration);
                if (cursor.isAfter(now) && isFree(cursor, blockEnd, busyPeriods)) {
                    free.add(new AvailableSlot(cursor, blockEnd));
                }
                    cursor = blockEnd;
            }
        }
        return free;
    }

    // #217: dos franjas que se solapan el mismo día se tratan como una sola (su unión). Las contiguas
    // se dejan separadas para no cambiar los huecos que ya se ofrecían.
    private static List<WorkingWindow> mergeOverlapping(List<WorkingWindow> sorted) {
        List<WorkingWindow> merged = new ArrayList<>();
        for (WorkingWindow window : sorted) {
            WorkingWindow last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && window.startTime().isBefore(last.endTime())) {
                LocalTime end = window.endTime().isAfter(last.endTime()) ? window.endTime() : last.endTime();
                merged.set(merged.size() - 1, new WorkingWindow(last.dayOfWeek(), last.startTime(), end));
            } else {
                merged.add(window);
            }
        }
        return merged;
    }

    private static boolean isFree(LocalDateTime start, LocalDateTime end, List<BusyPeriod> busyPeriods) {
        return busyPeriods.stream().noneMatch(busy -> busy.overlaps(start, end));
    }
}