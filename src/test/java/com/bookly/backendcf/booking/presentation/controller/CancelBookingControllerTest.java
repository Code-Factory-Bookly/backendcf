
package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.booking.application.service.CancelBookingService;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelBookingControllerTest {

    @Mock
    private CancelBookingService cancelBookingService;

    @InjectMocks
    private CancelBookingController controller;

    private UUID customerId;
    private UUID bookingId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        authentication = new UsernamePasswordAuthenticationToken(
                customerId.toString(),
                null
        );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCancelBookingSuccessfully() {
        Booking booking = new Booking(
                customerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(1)
        );

        LocalDateTime cancelledAt = LocalDateTime.now();

        booking.setStatus(BookingStatus.CANCELADA);
        booking.setCancelledAt(cancelledAt);
        booking.setCancellationReason("Cambio de planes");

        when(cancelBookingService.cancelBooking(
                bookingId,
                authentication,
                "Cambio de planes"
        )).thenReturn(booking);

        ResponseEntity<CancelBookingController.CancelBookingResponse> response =
                controller.cancelBooking(
                        bookingId,
                        new CancelBookingController.CancelBookingRequest(
                                "Cambio de planes"
                        )
                );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CANCELADA", response.getBody().status());
        assertEquals(cancelledAt, response.getBody().cancelledAt());
        assertEquals(
                "Cambio de planes",
                response.getBody().cancellationReason()
        );

        verify(cancelBookingService, times(1)).cancelBooking(
                bookingId,
                authentication,
                "Cambio de planes"
        );
    }

    @Test
    void shouldAllowRequestWithoutReason() {
        Booking booking = new Booking(
                customerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(1)
        );

        booking.setStatus(BookingStatus.CANCELADA);
        booking.setCancelledAt(LocalDateTime.now());

        when(cancelBookingService.cancelBooking(
                bookingId,
                authentication,
                null
        )).thenReturn(booking);

        ResponseEntity<CancelBookingController.CancelBookingResponse> response =
                controller.cancelBooking(bookingId, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CANCELADA", response.getBody().status());
        assertNull(response.getBody().cancellationReason());
    }

    @Test
    void shouldPropagateAccessDeniedForAnotherCustomer() {
        when(cancelBookingService.cancelBooking(
                bookingId,
                authentication,
                null
        )).thenThrow(
                new AccessDeniedException(
                        "Solo el cliente propietario puede cancelar la reserva"
                )
        );

        assertThrows(
                AccessDeniedException.class,
                () -> controller.cancelBooking(bookingId, null)
        );

        verify(cancelBookingService, times(1)).cancelBooking(
                bookingId,
                authentication,
                null
        );
    }
}
