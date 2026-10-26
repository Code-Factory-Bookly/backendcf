package com.bookly.backendcf.notification.application;

import java.util.Optional;
import java.util.UUID;

/** Quien participa en una reserva, para decidir quien puede ver su historial de correos. */
public interface BookingParticipantsLookup {

    Optional<Participants> find(UUID bookingId);

    record Participants(UUID customerId, UUID professionalId) {
    }
}
