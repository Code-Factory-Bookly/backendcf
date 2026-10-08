package com.bookly.backendcf.booking.domain.events;

import java.time.LocalDateTime;
import java.util.UUID;

public final class BookingCancelledEvent implements BookingEvent {
    private final UUID bookingId;
    private final String reason;
    private final LocalDateTime occurredAt;

    public BookingCancelledEvent(UUID bookingId, String reason) {
        this.bookingId = bookingId;
        this.reason = reason;
        this.occurredAt = LocalDateTime.now();
    }

    @Override
    public UUID getBookingId() { return bookingId; }
    @Override
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getReason() { return reason; }
}