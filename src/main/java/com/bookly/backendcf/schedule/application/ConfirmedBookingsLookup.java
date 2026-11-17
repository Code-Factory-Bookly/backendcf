package com.bookly.backendcf.schedule.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Reservas confirmadas que impiden un bloqueo (HU-05), con lo necesario para avisar al profesional. */
public interface ConfirmedBookingsLookup {

    List<AffectedBooking> findConfirmed(UUID professionalId, LocalDateTime from, LocalDateTime to);

    record AffectedBooking(
            UUID bookingId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String customerName,
            String serviceName) {
    }
}
