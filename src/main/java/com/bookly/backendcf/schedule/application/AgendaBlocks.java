package com.bookly.backendcf.schedule.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Consulta de bloqueos de agenda (HU-05) para los módulos que ofrecen o crean reservas
 * (disponibilidad y booking), sin que dependan del repositorio de schedule.
 */
public interface AgendaBlocks {

    /** Sin bloqueos: lo usan los constructores que no reciben el puerto. */
    AgendaBlocks NONE = (professionalId, from, to) -> List.of();

    List<BlockedPeriod> findOverlapping(UUID professionalId, LocalDateTime from, LocalDateTime to);

    record BlockedPeriod(LocalDateTime startTime, LocalDateTime endTime) {
    }
}
