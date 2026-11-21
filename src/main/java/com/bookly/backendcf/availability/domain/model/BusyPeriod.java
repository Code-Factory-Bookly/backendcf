package com.bookly.backendcf.availability.domain.model;

import java.time.LocalDateTime;

/** Periodo ya ocupado: una reserva confirmada o un bloqueo de agenda del profesional (HU-05). */
public record BusyPeriod(LocalDateTime startTime, LocalDateTime endTime) {

    /** Dos periodos se solapan solo si comparten tiempo; los contiguos no se solapan. */
    public boolean overlaps(LocalDateTime start, LocalDateTime end) {
        return startTime.isBefore(end) && endTime.isAfter(start);
    }
}