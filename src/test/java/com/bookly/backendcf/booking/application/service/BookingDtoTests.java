package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingRequest;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookingDtoTests {

    // ===== CreateBookingRequest Tests =====
    @Test
    void createBookingRequestConstructor() {
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        CreateBookingRequest req = new CreateBookingRequest(profId, svcId, start, end);

        assertEquals(profId, req.getProfessionalId());
        assertEquals(svcId, req.getServiceId());
        assertEquals(start, req.getStartTime());
        assertEquals(end, req.getEndTime());
    }

    @Test
    void createBookingRequestSetters() {
        CreateBookingRequest req = new CreateBookingRequest();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        req.setProfessionalId(profId);
        req.setServiceId(svcId);
        req.setStartTime(start);
        req.setEndTime(end);

        assertEquals(profId, req.getProfessionalId());
        assertEquals(svcId, req.getServiceId());
        assertEquals(start, req.getStartTime());
        assertEquals(end, req.getEndTime());
    }

    @Test
    void createBookingRequestEmptyConstructor() {
        CreateBookingRequest req = new CreateBookingRequest();
        assertNotNull(req);
    }

    // ===== CreateBookingResponse Tests =====
    @Test
    void createBookingResponseConstructor() {
        UUID bookingId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);
        LocalDateTime created = LocalDateTime.now();
        String status = "CONFIRMADA";

        CreateBookingResponse resp = new CreateBookingResponse(
                bookingId, custId, profId, svcId, start, end, status, created
        );

        assertEquals(bookingId, resp.getId());
        assertEquals(custId, resp.getCustomerId());
        assertEquals(profId, resp.getProfessionalId());
        assertEquals(svcId, resp.getServiceId());
        assertEquals(start, resp.getStartTime());
        assertEquals(end, resp.getEndTime());
        assertEquals(status, resp.getStatus());
        assertEquals(created, resp.getCreatedAt());
    }

    // ===== Booking Entity Tests =====
    @Test
    void bookingConstructor() {
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        Booking booking = new Booking(custId, profId, svcId, start, end);

        assertEquals(custId, booking.getCustomerId());
        assertEquals(profId, booking.getProfessionalId());
        assertEquals(svcId, booking.getServiceId());
        assertEquals(start, booking.getStartTime());
        assertEquals(end, booking.getEndTime());
        assertEquals(BookingStatus.CONFIRMADA, booking.getStatus());
        assertNotNull(booking.getCreatedAt());
        assertNotNull(booking.getUpdatedAt());
        assertNull(booking.getCancelledAt());
        assertNull(booking.getCancellationReason());
    }

    @Test
    void bookingEmptyConstructor() {
        Booking booking = new Booking();
        assertNotNull(booking);
    }

    @Test
    void bookingStatusSetter() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.CONFIRMADA);
        assertEquals(BookingStatus.CONFIRMADA, booking.getStatus());
    }

    @Test
    void bookingUpdatedAtSetter() {
        Booking booking = new Booking();
        LocalDateTime now = LocalDateTime.now();
        booking.setUpdatedAt(now);
        assertEquals(now, booking.getUpdatedAt());
    }

    @Test
    void bookingCancelledAtSetter() {
        Booking booking = new Booking();
        LocalDateTime cancelled = LocalDateTime.now();
        booking.setCancelledAt(cancelled);
        assertEquals(cancelled, booking.getCancelledAt());
    }

    @Test
    void bookingCancellationReasonSetter() {
        Booking booking = new Booking();
        String reason = "User requested cancellation";
        booking.setCancellationReason(reason);
        assertEquals(reason, booking.getCancellationReason());
    }

    @Test
    void bookingStartTimeSetter() {
        Booking booking = new Booking();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        booking.setStartTime(start);
        assertEquals(start, booking.getStartTime());
    }

    @Test
    void bookingEndTimeSetter() {
        Booking booking = new Booking();
        LocalDateTime end = LocalDateTime.now().plusDays(1).plusHours(1);
        booking.setEndTime(end);
        assertEquals(end, booking.getEndTime());
    }

    @Test
    void bookingGetId() {
        Booking booking = new Booking();
        UUID id = booking.getId();
        // ID is auto-generated, so just verify it's accessed
        assertNull(id); // Before persistence
    }

    // ===== BookingCreatedEvent Tests =====
    @Test
    void bookingCreatedEventConstructor() {
        UUID bookingId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        BookingCreatedEvent event = new BookingCreatedEvent(
                bookingId, custId, profId, svcId, start, end
        );

        assertEquals(bookingId, event.getBookingId());
        assertEquals(custId, event.getCustomerId());
        assertEquals(profId, event.getProfessionalId());
        assertEquals(svcId, event.getServiceId());
        assertEquals(start, event.getStartTime());
        assertEquals(end, event.getEndTime());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void bookingCreatedEventOccurredAtIsSet() {
        UUID bookingId = UUID.randomUUID();
        LocalDateTime beforeCreate = LocalDateTime.now();

        BookingCreatedEvent event = new BookingCreatedEvent(
                bookingId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1)
        );

        LocalDateTime afterCreate = LocalDateTime.now();
        assertNotNull(event.getOccurredAt());
        assertTrue(event.getOccurredAt().isAfter(beforeCreate) || event.getOccurredAt().isEqual(beforeCreate));
        assertTrue(event.getOccurredAt().isBefore(afterCreate) || event.getOccurredAt().isEqual(afterCreate));
    }

    // ===== Integration Tests =====
    @Test
    void bookingToResponseMapping() {
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        Booking booking = new Booking(custId, profId, svcId, start, end);

        // Simulate response creation from booking
        CreateBookingResponse response = new CreateBookingResponse(
                booking.getId(), booking.getCustomerId(), booking.getProfessionalId(),
                booking.getServiceId(), booking.getStartTime(), booking.getEndTime(),
                booking.getStatus().toString(), booking.getCreatedAt()
        );

        assertEquals(booking.getCustomerId(), response.getCustomerId());
        assertEquals(booking.getProfessionalId(), response.getProfessionalId());
        assertEquals(booking.getServiceId(), response.getServiceId());
    }

    @Test
    void requestToEventMapping() {
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        CreateBookingRequest request = new CreateBookingRequest(profId, svcId, start, end);

        UUID custId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        BookingCreatedEvent event = new BookingCreatedEvent(
                bookingId, custId, request.getProfessionalId(),
                request.getServiceId(), request.getStartTime(), request.getEndTime()
        );

        assertEquals(request.getProfessionalId(), event.getProfessionalId());
        assertEquals(request.getServiceId(), event.getServiceId());
        assertEquals(request.getStartTime(), event.getStartTime());
        assertEquals(request.getEndTime(), event.getEndTime());
    }

    @Test
    void bookingStatusEnum() {
        assertEquals("CONFIRMADA", BookingStatus.CONFIRMADA.name());
        assertNotNull(BookingStatus.valueOf("CONFIRMADA"));
    }
}