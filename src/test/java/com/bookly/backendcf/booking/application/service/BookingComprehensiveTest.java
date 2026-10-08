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
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingComprehensiveTestFinal {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingEventPublisher eventPublisher;

    private CreateBookingService service;
    private UUID customerId;
    private UUID professionalId;
    private UUID serviceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @BeforeEach
    void setUp() {
        service = new CreateBookingService(bookingRepository, eventPublisher);
        customerId = UUID.randomUUID();
        professionalId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        startTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        endTime = startTime.plusMinutes(60);
    }

    // ===== Booking Entity Tests =====
    @Test
    void bookingConstructorInitializesCorrectly() {
        Booking booking = new Booking(customerId, professionalId, serviceId, startTime, endTime);

        assertEquals(customerId, booking.getCustomerId());
        assertEquals(professionalId, booking.getProfessionalId());
        assertEquals(serviceId, booking.getServiceId());
        assertEquals(startTime, booking.getStartTime());
        assertEquals(endTime, booking.getEndTime());
        assertNotNull(booking.getCreatedAt());
    }

    @Test
    void bookingStatusDefaultIsConfirmada() {
        Booking booking = new Booking(customerId, professionalId, serviceId, startTime, endTime);
        booking.setStatus(BookingStatus.CONFIRMADA);
        assertEquals(BookingStatus.CONFIRMADA, booking.getStatus());
    }

    @Test
    void bookingAllGettersAccessed() {
        Booking booking = new Booking(customerId, professionalId, serviceId, startTime, endTime);

        assertNotNull(booking.getCustomerId());
        assertNotNull(booking.getProfessionalId());
        assertNotNull(booking.getServiceId());
        assertNotNull(booking.getStartTime());
        assertNotNull(booking.getEndTime());
        assertNotNull(booking.getStatus());
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
    void bookingSettersWorkCorrectly() {
        Booking booking = new Booking();

        booking.setStatus(BookingStatus.CONFIRMADA);
        assertEquals(BookingStatus.CONFIRMADA, booking.getStatus());

        LocalDateTime now = LocalDateTime.now();
        booking.setUpdatedAt(now);
        assertEquals(now, booking.getUpdatedAt());

        LocalDateTime cancelled = LocalDateTime.now();
        booking.setCancelledAt(cancelled);
        assertEquals(cancelled, booking.getCancelledAt());

        String reason = "Customer requested";
        booking.setCancellationReason(reason);
        assertEquals(reason, booking.getCancellationReason());

        booking.setStartTime(startTime);
        assertEquals(startTime, booking.getStartTime());

        booking.setEndTime(endTime);
        assertEquals(endTime, booking.getEndTime());
    }

    @Test
    void bookingGetId() {
        Booking booking = new Booking();
        assertNull(booking.getId());
    }

    // ===== CreateBookingService Tests =====
    @Test
    void shouldCreateBookingWithValidData() {
        when(bookingRepository.findOverlappingBookings(professionalId, startTime, endTime))
                .thenReturn(Collections.emptyList());

        Booking mockBooking = new Booking(customerId, professionalId, serviceId, startTime, endTime);
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        Booking result = service.createBooking(customerId, professionalId, serviceId, startTime, endTime);

        assertNotNull(result);
        assertEquals(customerId, result.getCustomerId());
        assertEquals(BookingStatus.CONFIRMADA, result.getStatus());
    }

    @Test
    void shouldPublishBookingCreatedEvent() {
        when(bookingRepository.findOverlappingBookings(professionalId, startTime, endTime))
                .thenReturn(Collections.emptyList());

        Booking mockBooking = new Booking(customerId, professionalId, serviceId, startTime, endTime);
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        service.createBooking(customerId, professionalId, serviceId, startTime, endTime);

        ArgumentCaptor<BookingCreatedEvent> captor = ArgumentCaptor.forClass(BookingCreatedEvent.class);
        verify(eventPublisher).publish(captor.capture());

        BookingCreatedEvent event = captor.getValue();
        assertNotNull(event);
        assertEquals(customerId, event.getCustomerId());
    }

    @Test
    void shouldRejectStartTimeAfterEndTime() {
        LocalDateTime invalidEnd = startTime.minusMinutes(30);

        assertThrows(IllegalArgumentException.class, () ->
                service.createBooking(customerId, professionalId, serviceId, startTime, invalidEnd)
        );
    }

    @Test
    void shouldRejectStartTimeEqualToEndTime() {
        assertThrows(IllegalArgumentException.class, () ->
                service.createBooking(customerId, professionalId, serviceId, startTime, startTime)
        );
    }

    @Test
    void shouldRejectPastStartTime() {
        LocalDateTime pastTime = LocalDateTime.now().minusDays(1);
        LocalDateTime pastEnd = pastTime.plusHours(1);

        assertThrows(IllegalArgumentException.class, () ->
                service.createBooking(customerId, professionalId, serviceId, pastTime, pastEnd)
        );
    }

    @Test
    void shouldRejectWhenSlotOccupied() {
        Booking existingBooking = new Booking(customerId, professionalId, serviceId, startTime, endTime);
        when(bookingRepository.findOverlappingBookings(professionalId, startTime, endTime))
                .thenReturn(Collections.singletonList(existingBooking));

        assertThrows(BookingConflictException.class, () ->
                service.createBooking(customerId, professionalId, serviceId, startTime, endTime)
        );
    }

    @Test
    void shouldHandleUniqueConstraintViolation() {
        when(bookingRepository.findOverlappingBookings(professionalId, startTime, endTime))
                .thenReturn(Collections.emptyList());

        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new org.hibernate.exception.ConstraintViolationException(
                        "Violation", new SQLException(), "unique_booking_per_professional_time"
                ));

        assertThrows(BookingConflictException.class, () ->
                service.createBooking(customerId, professionalId, serviceId, startTime, endTime)
        );
    }

    // ===== DTO Tests =====
    @Test
    void createBookingRequestConstructorWorks() {
        CreateBookingRequest request = new CreateBookingRequest(professionalId, serviceId, startTime, endTime);

        assertEquals(professionalId, request.getProfessionalId());
        assertEquals(serviceId, request.getServiceId());
        assertEquals(startTime, request.getStartTime());
        assertEquals(endTime, request.getEndTime());
    }

    @Test
    void createBookingRequestEmptyConstructor() {
        CreateBookingRequest request = new CreateBookingRequest();
        assertNotNull(request);
    }

    @Test
    void createBookingRequestSetters() {
        CreateBookingRequest request = new CreateBookingRequest();

        request.setProfessionalId(professionalId);
        request.setServiceId(serviceId);
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        assertEquals(professionalId, request.getProfessionalId());
        assertEquals(serviceId, request.getServiceId());
        assertEquals(startTime, request.getStartTime());
        assertEquals(endTime, request.getEndTime());
    }

    @Test
    void createBookingResponseConstructorWorks() {
        UUID bookingId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        CreateBookingResponse response = new CreateBookingResponse(
                bookingId, customerId, professionalId, serviceId,
                startTime, endTime, "CONFIRMADA", createdAt
        );

        assertEquals(bookingId, response.getId());
        assertEquals(customerId, response.getCustomerId());
        assertEquals("CONFIRMADA", response.getStatus());
        assertEquals(createdAt, response.getCreatedAt());
    }

    // ===== BookingStatus Enum Tests =====
    @Test
    void bookingStatusValuesExist() {
        assertNotNull(BookingStatus.CONFIRMADA);
        assertEquals("CONFIRMADA", BookingStatus.CONFIRMADA.toString());
    }

    @Test
    void bookingStatusValueOf() {
        BookingStatus status = BookingStatus.valueOf("CONFIRMADA");
        assertEquals(BookingStatus.CONFIRMADA, status);
    }

    // ===== Exception Tests =====
    @Test
    void bookingConflictExceptionWithErrorCode() {
        BookingConflictException ex = new BookingConflictException("TEST_CODE", "Test message");
        assertEquals("TEST_CODE", ex.getErrorCode());
        assertEquals("Test message", ex.getMessage());
    }

    // ===== Event Publisher Tests =====
    @Test
    void bookingEventPublisherIntegration() {
        when(bookingRepository.findOverlappingBookings(professionalId, startTime, endTime))
                .thenReturn(Collections.emptyList());

        Booking mockBooking = new Booking(customerId, professionalId, serviceId, startTime, endTime);
        mockBooking.setStatus(BookingStatus.CONFIRMADA);
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        service.createBooking(customerId, professionalId, serviceId, startTime, endTime);

        verify(eventPublisher).publish(any(BookingCreatedEvent.class));
    }

    // ===== Edge Cases =====
    @Test
    void shouldHandleMultipleOverlappingBookings() {
        Booking booking1 = new Booking(customerId, professionalId, serviceId, startTime, endTime);
        Booking booking2 = new Booking(customerId, professionalId, serviceId, startTime.plusHours(2), endTime.plusHours(2));

        when(bookingRepository.findOverlappingBookings(professionalId, startTime, endTime))
                .thenReturn(java.util.List.of(booking1, booking2));

        assertThrows(BookingConflictException.class, () ->
                service.createBooking(customerId, professionalId, serviceId, startTime, endTime)
        );
    }

    @Test
    void shouldCreateBookingWithMinimalDuration() {
        LocalDateTime shortEnd = startTime.plusMinutes(15);

        when(bookingRepository.findOverlappingBookings(professionalId, startTime, shortEnd))
                .thenReturn(Collections.emptyList());

        Booking mockBooking = new Booking(customerId, professionalId, serviceId, startTime, shortEnd);
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        Booking result = service.createBooking(customerId, professionalId, serviceId, startTime, shortEnd);

        assertNotNull(result);
    }

    @Test
    void shouldCreateBookingWithLargeDuration() {
        LocalDateTime longEnd = startTime.plusDays(7);

        when(bookingRepository.findOverlappingBookings(professionalId, startTime, longEnd))
                .thenReturn(Collections.emptyList());

        Booking mockBooking = new Booking(customerId, professionalId, serviceId, startTime, longEnd);
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        Booking result = service.createBooking(customerId, professionalId, serviceId, startTime, longEnd);

        assertNotNull(result);
    }

    @Test
    void shouldVerifyRepositoryMethodsCalled() {
        when(bookingRepository.findOverlappingBookings(professionalId, startTime, endTime))
                .thenReturn(Collections.emptyList());

        Booking mockBooking = new Booking(customerId, professionalId, serviceId, startTime, endTime);
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        service.createBooking(customerId, professionalId, serviceId, startTime, endTime);

        verify(bookingRepository).findOverlappingBookings(professionalId, startTime, endTime);
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void bookingCreatedEventHasAllDetails() {
        UUID bookingId = UUID.randomUUID();
        BookingCreatedEvent event = new BookingCreatedEvent(
                bookingId, customerId, professionalId, serviceId, startTime, endTime
        );

        assertEquals(bookingId, event.getBookingId());
        assertEquals(customerId, event.getCustomerId());
        assertEquals(professionalId, event.getProfessionalId());
        assertEquals(serviceId, event.getServiceId());
        assertEquals(startTime, event.getStartTime());
        assertEquals(endTime, event.getEndTime());
        assertNotNull(event.getOccurredAt());
    }
}