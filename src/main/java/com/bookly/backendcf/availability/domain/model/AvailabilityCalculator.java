package com.bookly.backendcf.availability.domain.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

        List<WorkingWindow> dayWindows = windows.stream()
                .filter(window -> window.dayOfWeek() == date.getDayOfWeek())
                .sorted(Comparator.comparing(WorkingWindow::startTime))
                .toList();

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

    private static boolean isFree(LocalDateTime start, LocalDateTime end, List<BusyPeriod> busyPeriods) {
        return busyPeriods.stream().noneMatch(busy -> busy.overlaps(start, end));
    }
}