package com.bookly.backendcf.booking.domain.model;

import java.util.List;

public enum BookingStatus {
    CONFIRMADA("Reserva confirmada"),
    CANCELADA("Reserva cancelada"),
    RESCHEDULED("Reserva reprogramada"),
    COMPLETED("Reserva completada");

    /**
     * #220: única definición de qué reservas ocupan agenda. La usan la disponibilidad (HU-07), la
     * creación de reservas (HU-08), los bloqueos (HU-05) y el reporte de ocupación (HU-15). Solo la
     * cancelada libera la franja. Si cambia, hay que cambiar también la restricción de V47.
     */
    public static final List<BookingStatus> OCCUPYING = List.of(CONFIRMADA, RESCHEDULED, COMPLETED);

    private final String description;

    BookingStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}