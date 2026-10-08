
package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.application.service.CreateBookingService;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingRequest;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private CreateBookingService createBookingService;

    @InjectMocks
    private BookingController controller;

    private UUID customerId;
    private UUID professionalId;
    private UUID serviceId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private CreateBookingRequest request;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        professionalId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        startTime = LocalDateTime.now().plusDays(2);
        endTime = startTime.plusMinutes(45);

        request = new CreateBookingRequest(
                professionalId,
                serviceId,
                startTime,
                endTime
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        customerId.toString(),
                        null
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateBookingSuccessfully() {
        Booking booking = new Booking(
                customerId,
                professionalId,
                serviceId,
                startTime,
                endTime
        );

        when(createBookingService.createBooking(
                customerId,
                professionalId,
                serviceId,
                startTime,
                endTime
        )).thenReturn(booking);

        ResponseEntity<?> response =
                controller.createBooking(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        assertInstanceOf(
                CreateBookingResponse.class,
                response.getBody()
        );

        CreateBookingResponse body =
                (CreateBookingResponse) response.getBody();

        assertEquals(customerId, body.getCustomerId());
        assertEquals(professionalId, body.getProfessionalId());
        assertEquals(serviceId, body.getServiceId());
        assertEquals(startTime, body.getStartTime());
        assertEquals(endTime, body.getEndTime());
        assertEquals(
                BookingStatus.CONFIRMADA.toString(),
                body.getStatus()
        );
        assertNotNull(body.getCreatedAt());

        verify(createBookingService, times(1))
                .createBooking(
                        customerId,
                        professionalId,
                        serviceId,
                        startTime,
                        endTime
                );
    }

    @Test
    void shouldReturnConflictWhenSlotIsOccupied() {
        when(createBookingService.createBooking(
                any(), any(), any(), any(), any()
        )).thenThrow(
                new BookingConflictException(
                        "CONFLICT",
                        "Slot occupied"
                )
        );

        ResponseEntity<?> response =
                controller.createBooking(request);

        assertEquals(
                HttpStatus.CONFLICT,
                response.getStatusCode()
        );
        assertNotNull(response.getBody());
        assertTrue(
                response.getBody().toString()
                        .contains("CONFLICT")
        );

        verify(createBookingService, times(1))
                .createBooking(
                        customerId,
                        professionalId,
                        serviceId,
                        startTime,
                        endTime
                );
    }

    @Test
    void shouldReturnBadRequestForInvalidTimeRange() {
        when(createBookingService.createBooking(
                any(), any(), any(), any(), any()
        )).thenThrow(
                new IllegalArgumentException(
                        "Invalid time range"
                )
        );

        ResponseEntity<?> response =
                controller.createBooking(request);

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );
        assertNotNull(response.getBody());
        assertTrue(
                response.getBody().toString()
                        .contains("INVALID_REQUEST")
        );
    }

    @Test
    void shouldReturnBadRequestForInvalidCustomerId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "invalid-uuid",
                        null
                )
        );

        ResponseEntity<?> response =
                controller.createBooking(request);

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        verifyNoInteractions(createBookingService);
    }

    @Test
    void shouldUseAuthenticatedCustomerId() {
        Booking booking = new Booking(
                customerId,
                professionalId,
                serviceId,
                startTime,
                endTime
        );

        when(createBookingService.createBooking(
                eq(customerId),
                eq(professionalId),
                eq(serviceId),
                eq(startTime),
                eq(endTime)
        )).thenReturn(booking);

        ResponseEntity<?> response =
                controller.createBooking(request);

        assertEquals(
                HttpStatus.CREATED,
                response.getStatusCode()
        );

        verify(createBookingService)
                .createBooking(
                        customerId,
                        professionalId,
                        serviceId,
                        startTime,
                        endTime
                );
    }

    @Test
    void shouldValidateBookingStatus() {
        Booking booking = new Booking(
                customerId,
                professionalId,
                serviceId,
                startTime,
                endTime
        );

        assertEquals(
                BookingStatus.CONFIRMADA,
                booking.getStatus()
        );
    }
}
