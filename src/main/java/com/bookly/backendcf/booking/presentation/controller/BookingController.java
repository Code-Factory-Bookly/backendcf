package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.booking.application.service.CreateBookingService;
import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingRequest;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingResponse;
import com.bookly.backendcf.booking.domain.model.Booking;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservas")
public class BookingController {

    private final CreateBookingService createBookingService;

    public BookingController(CreateBookingService createBookingService) {
        this.createBookingService = createBookingService;
    }

    @PostMapping
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            Authentication authentication) {

        try {
            UUID customerId = UUID.fromString(authentication.getName());

            Booking booking = createBookingService.createBooking(
                    customerId,
                    request.getProfessionalId(),
                    request.getServiceId(),
                    request.getStartTime(),
                    request.getEndTime()
            );

            CreateBookingResponse response = new CreateBookingResponse(
                    booking.getId(),
                    booking.getCustomerId(),
                    booking.getProfessionalId(),
                    booking.getServiceId(),
                    booking.getStartTime(),
                    booking.getEndTime(),
                    booking.getStatus().toString(),
                    booking.getCreatedAt()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (BookingConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new ErrorResponse(
                            e.getErrorCode(),
                            e.getMessage(),
                            "Intenta consultar disponibilidad con HU-07"
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse("FRANJA_INVÁLIDA", e.getMessage(), null)
            );
        }
    }
}

record ErrorResponse(String error, String message, String hint) {}