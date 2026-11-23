package com.bookly.backendcf.booking.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * "Mi Agenda" del profesional (HU-14): reservas del periodo consultado, en orden cronologico.
 *
 * <p>Solo expone lo que el profesional necesita para planificar su jornada (hora, nombre del
 * cliente y servicio); no incluye identificadores de cuentas ajenas ni datos de pago. Si no hay
 * reservas, {@code content} es una lista vacia y {@code message} informa que la agenda esta libre.
 */
public record ProfessionalAgendaResponse(
        LocalDate from,
        LocalDate to,
        List<Item> content,
        String message) {

    public record Item(
            UUID id,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String customerName,
            String serviceName,
            String status) {
    }
}
