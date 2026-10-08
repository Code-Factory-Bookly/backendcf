package com.bookly.backendcf.booking.domain.events;

import java.time.LocalDateTime;
import java.util.UUID;

public final class BookingRescheduledEvent implements BookingEvent {
    private final UUID bookingId;
    private final LocalDateTime oldStartTime;
    private final LocalDateTime oldEndTime;
    private final LocalDateTime newStartTime;
    private final LocalDateTime newEndTime;
    private final LocalDateTime occurredAt;

    public BookingRescheduledEvent(UUID bookingId, LocalDateTime oldStartTime, LocalDateTime oldEndTime,
                                   LocalDateTime newStartTime, LocalDateTime newEndTime) {
        this.bookingId = bookingId;
        this.oldStartTime = oldStartTime;
        this.oldEndTime = oldEndTime;
        this.newStartTime = newStartTime;
        this.newEndTime = newEndTime;
        this.occurredAt = LocalDateTime.now();
    }

    @Override
    public UUID getBookingId() { return bookingId; }
    @Override
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public LocalDateTime getOldStartTime() { return oldStartTime; }
    public LocalDateTime getOldEndTime() { return oldEndTime; }
    public LocalDateTime getNewStartTime() { return newStartTime; }
    public LocalDateTime getNewEndTime() { return newEndTime; }
}