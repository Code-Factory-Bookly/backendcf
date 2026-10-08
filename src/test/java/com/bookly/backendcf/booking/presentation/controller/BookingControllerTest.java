package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.booking.application.service.CreateBookingService;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private CreateBookingService createBookingService;

    private UUID customerId;
    private UUID professionalId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        professionalId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
    }

    @Test
    void shouldCreateBooking() {
        Booking mockBooking = new Booking(customerId, professionalId, serviceId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusMinutes(45));

        when(createBookingService.createBooking(any(), any(), any(), any(), any()))
                .thenReturn(mockBooking);

        Booking result = createBookingService.createBooking(customerId, professionalId, serviceId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusMinutes(45));

        assertNotNull(result);
    }
}