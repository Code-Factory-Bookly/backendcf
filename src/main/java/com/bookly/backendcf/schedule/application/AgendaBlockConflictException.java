package com.bookly.backendcf.schedule.application;

import com.bookly.backendcf.shared.error.ResourceConflictException;
import java.util.Map;

/** HU-05: el rango a bloquear choca con reservas confirmadas o con otro bloqueo (409). */
public class AgendaBlockConflictException extends ResourceConflictException {

    private AgendaBlockConflictException(String errorCode, String message, Map<String, String> details) {
        super(errorCode, message, details);
    }

    /** details: una entrada por cita afectada (id de la reserva → fecha, hora, cliente y servicio). */
    public static AgendaBlockConflictException bookingsInRange(Map<String, String> affectedBookings) {
        return new AgendaBlockConflictException(
                "AGENDA_BLOCK_HAS_BOOKINGS",
                "Hay reservas confirmadas en el rango. Cancélalas o reprográmalas antes de bloquear.",
                affectedBookings);
    }

    public static AgendaBlockConflictException overlapsBlock(Map<String, String> blocks) {
        return new AgendaBlockConflictException(
                "AGENDA_BLOCK_OVERLAP", "Ya existe un bloqueo que se cruza con ese rango", blocks);
    }
}
