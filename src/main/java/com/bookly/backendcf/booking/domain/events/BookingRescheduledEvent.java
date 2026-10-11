package com.bookly.backendcf.booking.domain.events;

import java.time.LocalDateTime;
import java.util.UUID;

/** Contrato preparado para que el módulo de reservas publique reprogramaciones. */
public final class BookingRescheduledEvent implements BookingEvent {

    private final UUID eventId = UUID.randomUUID();
    private final UUID bookingId;
    private final UUID customerId;
    private final UUID professionalId;
    private final UUID serviceId;
    private final LocalDateTime previousStartTime;
    private final LocalDateTime previousEndTime;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final LocalDateTime occurredAt = LocalDateTime.now();

    public BookingRescheduledEvent(UUID bookingId, UUID customerId, UUID professionalId,
                                   UUID serviceId, LocalDateTime previousStartTime,
                                   LocalDateTime previousEndTime, LocalDateTime startTime,
                                   LocalDateTime endTime) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.previousStartTime = previousStartTime;
        this.previousEndTime = previousEndTime;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override public UUID getEventId() { return eventId; }
    @Override public UUID getBookingId() { return bookingId; }
    @Override public LocalDateTime getOccurredAt() { return occurredAt; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProfessionalId() { return professionalId; }
    public UUID getServiceId() { return serviceId; }
    public LocalDateTime getPreviousStartTime() { return previousStartTime; }
    public LocalDateTime getPreviousEndTime() { return previousEndTime; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
}
