package com.bookly.backendcf.availability.domain.model;

import java.time.LocalDateTime;

/** Bloque de tiempo libre que un cliente puede reservar. */
public record AvailableSlot(LocalDateTime startTime, LocalDateTime endTime) {
}