package com.bookly.backendcf.booking.presentation.dto;

import com.bookly.backendcf.booking.domain.model.Booking;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID customerId,
        UUID professionalId,
        UUID serviceId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime cancelledAt,
        String cancellationReason) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getCustomerId(),
                booking.getProfessionalId(),
                booking.getServiceId(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus().name(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                booking.getCancelledAt(),
                booking.getCancellationReason());
    }
}
