package com.bookly.backendcf.booking.domain.events;

import java.time.LocalDateTime;
import java.util.UUID;

public final class BookingCreatedEvent implements BookingEvent {
    private final UUID bookingId;
    private final UUID customerId;
    private final UUID professionalId;
    private final UUID serviceId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final LocalDateTime occurredAt;

    public BookingCreatedEvent(UUID bookingId, UUID customerId, UUID professionalId, UUID serviceId,
                               LocalDateTime startTime, LocalDateTime endTime) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.occurredAt = LocalDateTime.now();
    }

    @Override
    public UUID getBookingId() { return bookingId; }
    @Override
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProfessionalId() { return professionalId; }
    public UUID getServiceId() { return serviceId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
}