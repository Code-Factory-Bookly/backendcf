package com.bookly.backendcf.booking.domain.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingRescheduledEventTest {

    @Test
    void conservaElHorarioAnteriorYElNuevo() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDateTime previousStart = LocalDateTime.of(2026, 11, 10, 9, 0);
        LocalDateTime previousEnd = previousStart.plusHours(1);
        LocalDateTime newStart = LocalDateTime.of(2026, 11, 12, 10, 0);
        LocalDateTime newEnd = newStart.plusHours(1);

        BookingRescheduledEvent event = new BookingRescheduledEvent(
                bookingId, customerId, professionalId, serviceId,
                previousStart, previousEnd, newStart, newEnd);

        assertNotNull(event.getEventId());
        assertEquals(bookingId, event.getBookingId());
        assertEquals(customerId, event.getCustomerId());
        assertEquals(professionalId, event.getProfessionalId());
        assertEquals(serviceId, event.getServiceId());
        assertEquals(previousStart, event.getPreviousStartTime());
        assertEquals(previousEnd, event.getPreviousEndTime());
        assertEquals(newStart, event.getStartTime());
        assertEquals(newEnd, event.getEndTime());
        assertNotNull(event.getOccurredAt());
    }
}
