package com.bookly.backendcf.booking.domain.events;

import java.time.LocalDateTime;
import java.util.UUID;

public sealed interface BookingEvent permits BookingCreatedEvent, BookingCancelledEvent, BookingRescheduledEvent {
    UUID getBookingId();
    LocalDateTime getOccurredAt();
}