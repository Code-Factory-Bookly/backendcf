package com.bookly.backendcf.report.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/** Bloqueo de agenda de un profesional (HU-05): tiempo que no cuenta como capacidad (#213). */
public record BlockedPeriod(UUID professionalId, LocalDateTime startTime, LocalDateTime endTime) {
}
