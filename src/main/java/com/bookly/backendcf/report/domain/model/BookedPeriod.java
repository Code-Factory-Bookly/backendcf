package com.bookly.backendcf.report.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/** Reserva que ocupa agenda de un profesional (CONFIRMADA o COMPLETED). */
public record BookedPeriod(UUID professionalId, LocalDateTime startTime, LocalDateTime endTime) {
}