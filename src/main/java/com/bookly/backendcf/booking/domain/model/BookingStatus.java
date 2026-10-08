package com.bookly.backendcf.booking.domain.model;

public enum BookingStatus {
    CONFIRMADA("Reserva confirmada"),
    CANCELADA("Reserva cancelada"),
    RESCHEDULED("Reserva reprogramada"),
    COMPLETED("Reserva completada");

    private final String description;

    BookingStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}