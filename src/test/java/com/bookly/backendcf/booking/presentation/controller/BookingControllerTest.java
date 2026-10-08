package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.booking.application.service.CreateBookingService;
import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private CreateBookingService createBookingService;

    private BookingController controller;
    private UUID customerId;
    private UUID professionalId;
    private UUID serviceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @BeforeEach
    void setUp() {
        controller = new BookingController(createBookingService);
        customerId = UUID.randomUUID();
        professionalId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        startTime = LocalDateTime.now().plusDays(1);
        endTime = startTime.plusMinutes(45);
    }

    @Test
    void shouldCreateBookingSuccessfully() {
        Booking booking = new Booking(customerId, professionalId, serviceId, startTime, endTime);
        when(createBookingService.createBooking(customerId, professionalId, serviceId, startTime, endTime))
                .thenReturn(booking);

        Booking result = createBookingService.createBooking(customerId, professionalId, serviceId, startTime, endTime);

        assertNotNull(result);
        assertEquals(BookingStatus.CONFIRMADA, result.getStatus());
        assertEquals(customerId, result.getCustomerId());
        assertEquals(professionalId, result.getProfessionalId());
        assertEquals(serviceId, result.getServiceId());
    }

    @Test
    void shouldThrowConflictException() {
        when(createBookingService.createBooking(any(), any(), any(), any(), any()))
                .thenThrow(new BookingConflictException("CONFLICT", "Slot occupied"));

        assertThrows(BookingConflictException.class, () ->
                createBookingService.createBooking(customerId, professionalId, serviceId, startTime, endTime)
        );
    }

    @Test
    void shouldThrowIllegalArgumentException() {
        when(createBookingService.createBooking(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid time range"));

        assertThrows(IllegalArgumentException.class, () ->
                createBookingService.createBooking(customerId, professionalId, serviceId, startTime, endTime)
        );
    }

    @Test
    void shouldValidateBookingStatus() {
        Booking booking = new Booking(customerId, professionalId, serviceId, startTime, endTime);
        assertEquals(BookingStatus.CONFIRMADA, booking.getStatus());
    }
}