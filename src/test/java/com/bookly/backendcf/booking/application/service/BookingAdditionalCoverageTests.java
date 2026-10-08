package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingRequest;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingAdditionalCoverageTests {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingEventPublisher eventPublisher;

    private CreateBookingService service;

    @BeforeEach
    void setUp() {
        service = new CreateBookingService(bookingRepository, eventPublisher);
    }

    // CreateBookingRequest extensive tests
    @Test
    void createBookingRequestAllFields() {
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        CreateBookingRequest req = new CreateBookingRequest(profId, svcId, start, end);

        assertNotNull(req.getProfessionalId());
        assertNotNull(req.getServiceId());
        assertNotNull(req.getStartTime());
        assertNotNull(req.getEndTime());
        assertEquals(profId, req.getProfessionalId());
        assertEquals(svcId, req.getServiceId());
        assertEquals(start, req.getStartTime());
        assertEquals(end, req.getEndTime());
    }

    @Test
    void createBookingRequestSettersIndividual() {
        CreateBookingRequest req = new CreateBookingRequest();
        UUID profId = UUID.randomUUID();

        req.setProfessionalId(profId);
        assertEquals(profId, req.getProfessionalId());

        UUID svcId = UUID.randomUUID();
        req.setServiceId(svcId);
        assertEquals(svcId, req.getServiceId());

        LocalDateTime start = LocalDateTime.now().plusDays(1);
        req.setStartTime(start);
        assertEquals(start, req.getStartTime());

        LocalDateTime end = start.plusHours(1);
        req.setEndTime(end);
        assertEquals(end, req.getEndTime());
    }

    // CreateBookingResponse extensive tests
    @Test
    void createBookingResponseAllFields() {
        UUID bookingId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);
        LocalDateTime created = LocalDateTime.now();

        CreateBookingResponse resp = new CreateBookingResponse(
                bookingId, custId, profId, svcId, start, end, "CONFIRMADA", created
        );

        assertEquals(bookingId, resp.getId());
        assertEquals(custId, resp.getCustomerId());
        assertEquals(profId, resp.getProfessionalId());
        assertEquals(svcId, resp.getServiceId());
        assertEquals(start, resp.getStartTime());
        assertEquals(end, resp.getEndTime());
        assertEquals("CONFIRMADA", resp.getStatus());
        assertEquals(created, resp.getCreatedAt());
    }

    @Test
    void createBookingResponseFieldAccess() {
        UUID id = UUID.randomUUID();
        CreateBookingResponse resp = new CreateBookingResponse(
                id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1),
                "CONFIRMADA", LocalDateTime.now()
        );

        assertNotNull(resp.getId());
        assertNotNull(resp.getCustomerId());
        assertNotNull(resp.getProfessionalId());
        assertNotNull(resp.getServiceId());
        assertNotNull(resp.getStartTime());
        assertNotNull(resp.getEndTime());
        assertNotNull(resp.getStatus());
        assertNotNull(resp.getCreatedAt());
    }

    // Booking entity extensive tests
    @Test
    void bookingConstructorFullInitialization() {
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
    void bookingEmptyConstructorCreation() {
        Booking booking = new Booking();
        assertNotNull(booking);
    }

    @Test
    void bookingStatusTransitions() {
        Booking booking = new Booking();

        booking.setStatus(BookingStatus.CONFIRMADA);
        assertEquals(BookingStatus.CONFIRMADA, booking.getStatus());
    }

    @Test
    void bookingCancellationFields() {
        Booking booking = new Booking();
        LocalDateTime cancelled = LocalDateTime.now();

        booking.setCancelledAt(cancelled);
        assertEquals(cancelled, booking.getCancelledAt());

        String reason = "Customer requested cancellation";
        booking.setCancellationReason(reason);
        assertEquals(reason, booking.getCancellationReason());
    }

    @Test
    void bookingTimeFieldUpdates() {
        Booking booking = new Booking();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(2);

        booking.setStartTime(start);
        booking.setEndTime(end);

        assertEquals(start, booking.getStartTime());
        assertEquals(end, booking.getEndTime());
    }

    @Test
    void bookingUpdatedAtField() {
        Booking booking = new Booking();
        LocalDateTime updated = LocalDateTime.now();

        booking.setUpdatedAt(updated);
        assertEquals(updated, booking.getUpdatedAt());
    }

    // BookingCreatedEvent extensive tests
    @Test
    void bookingCreatedEventFullInitialization() {
        UUID bookingId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        BookingCreatedEvent event = new BookingCreatedEvent(bookingId, custId, profId, svcId, start, end);

        assertEquals(bookingId, event.getBookingId());
        assertEquals(custId, event.getCustomerId());
        assertEquals(profId, event.getProfessionalId());
        assertEquals(svcId, event.getServiceId());
        assertEquals(start, event.getStartTime());
        assertEquals(end, event.getEndTime());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void bookingCreatedEventOccurredAtTimestamp() {
        LocalDateTime before = LocalDateTime.now();

        BookingCreatedEvent event = new BookingCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1)
        );

        LocalDateTime after = LocalDateTime.now();

        assertNotNull(event.getOccurredAt());
        assertTrue(event.getOccurredAt().isAfter(before.minusSeconds(1)));
        assertTrue(event.getOccurredAt().isBefore(after.plusSeconds(1)));
    }

    @Test
    void bookingCreatedEventAllFieldsAccessible() {
        UUID bookingId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();

        BookingCreatedEvent event = new BookingCreatedEvent(
                bookingId, custId, profId, svcId,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1)
        );

        assertNotNull(event.getBookingId());
        assertNotNull(event.getCustomerId());
        assertNotNull(event.getProfessionalId());
        assertNotNull(event.getServiceId());
        assertNotNull(event.getStartTime());
        assertNotNull(event.getEndTime());
        assertNotNull(event.getOccurredAt());
    }

    // BookingConflictException tests
    @Test
    void bookingConflictExceptionConstruction() {
        String code = "CONFLICT_001";
        String msg = "Booking conflicts with existing reservation";

        BookingConflictException ex = new BookingConflictException(code, msg);

        assertEquals(code, ex.getErrorCode());
        assertEquals(msg, ex.getMessage());
    }

    @Test
    void bookingConflictExceptionThrown() {
        assertThrows(BookingConflictException.class, () -> {
            throw new BookingConflictException("ERR", "Error");
        });
    }

    // CreateBookingService validation edge cases
    @Test
    void serviceRejectsInvertedTimes() {
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        LocalDateTime start = end.plusHours(1);

        assertThrows(IllegalArgumentException.class, () ->
                service.createBooking(custId, profId, svcId, start, end)
        );
    }

    @Test
    void serviceRejectsSameStartEnd() {
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime time = LocalDateTime.now().plusDays(1);

        assertThrows(IllegalArgumentException.class, () ->
                service.createBooking(custId, profId, svcId, time, time)
        );
    }

    @Test
    void serviceRejectsPastBooking() {
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        LocalDateTime future = past.plusHours(1);

        assertThrows(IllegalArgumentException.class, () ->
                service.createBooking(custId, profId, svcId, past, future)
        );
    }

    @Test
    void serviceHandlesMultipleConflicts() {
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        LocalDateTime end = start.plusHours(1);

        Booking b1 = new Booking(custId, profId, svcId, start, end);
        Booking b2 = new Booking(custId, profId, svcId, start.plusMinutes(30), end.plusMinutes(30));

        when(bookingRepository.findOverlappingBookings(profId, start, end))
                .thenReturn(List.of(b1, b2));

        assertThrows(BookingConflictException.class, () ->
                service.createBooking(custId, profId, svcId, start, end)
        );
    }

    @Test
    void servicePublishesEventOnSuccess() {
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        LocalDateTime end = start.plusHours(1);

        when(bookingRepository.findOverlappingBookings(profId, start, end))
                .thenReturn(Collections.emptyList());

        Booking mockBooking = new Booking(custId, profId, svcId, start, end);
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        service.createBooking(custId, profId, svcId, start, end);

        verify(eventPublisher, times(1)).publish(any(BookingCreatedEvent.class));
    }

    @Test
    void serviceRepositorySaveCalledOnce() {
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        LocalDateTime end = start.plusHours(1);

        when(bookingRepository.findOverlappingBookings(profId, start, end))
                .thenReturn(Collections.emptyList());

        Booking mockBooking = new Booking(custId, profId, svcId, start, end);
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        service.createBooking(custId, profId, svcId, start, end);

        verify(bookingRepository, times(1)).save(any(Booking.class));
    }

    @Test
    void bookingStatusEnum() {
        assertEquals("CONFIRMADA", BookingStatus.CONFIRMADA.name());
        BookingStatus status = BookingStatus.valueOf("CONFIRMADA");
        assertNotNull(status);
    }

    @Test
    void bookingStatusEnumValue() {
        BookingStatus status = BookingStatus.CONFIRMADA;
        assertNotNull(status.name());
        assertEquals("CONFIRMADA", status.toString());
    }

    @Test
    void createBookingRequestEmptyConstructor() {
        CreateBookingRequest request = new CreateBookingRequest();
        assertNotNull(request);
    }

    @Test
    void bookingIdAccess() {
        Booking booking = new Booking();
        UUID id = booking.getId();
        assertNull(id);
    }

    @Test
    void bookingCreatedAtNotNull() {
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        Booking booking = new Booking(custId, profId, svcId, start, end);
        assertNotNull(booking.getCreatedAt());
    }

    @Test
    void bookingUpdatedAtNotNull() {
        UUID custId = UUID.randomUUID();
        UUID profId = UUID.randomUUID();
        UUID svcId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        Booking booking = new Booking(custId, profId, svcId, start, end);
        assertNotNull(booking.getUpdatedAt());
    }
}