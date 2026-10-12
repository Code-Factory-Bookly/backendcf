package com.bookly.backendcf.availability.domain.model;

import java.time.LocalDateTime;

/** Periodo ya ocupado por una reserva confirmada del profesional. */
public record BusyPeriod(LocalDateTime startTime, LocalDateTime endTime) {

    /** Dos periodos se solapan solo si comparten tiempo; los contiguos no se solapan. */
    public boolean overlaps(LocalDateTime start, LocalDateTime end) {
        return startTime.isBefore(end) && endTime.isAfter(start);
    }
}