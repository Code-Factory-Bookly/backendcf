
package com.bookly.backendcf.booking.domain.events;

import java.time.LocalDateTime;
import java.util.UUID;

public final class BookingCancelledEvent implements BookingEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID professionalId;
    private final UUID serviceId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final LocalDateTime cancelledAt;
    private final String cancellationReason;
    private final LocalDateTime occurredAt;

    public BookingCancelledEvent(
            UUID bookingId,
            UUID customerId,
            UUID professionalId,
            UUID serviceId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime cancelledAt,
            String cancellationReason
    ) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.cancelledAt = cancelledAt;
        this.cancellationReason = cancellationReason;
        this.occurredAt = LocalDateTime.now();
    }

    @Override
    public UUID getBookingId() {
        return bookingId;
    }

    @Override
    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getProfessionalId() {
        return professionalId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
