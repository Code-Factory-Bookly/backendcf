package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.booking.application.service.CreateBookingService;
import com.bookly.backendcf.booking.application.service.GetBookingService;
import com.bookly.backendcf.booking.application.service.CancelBookingService;
import com.bookly.backendcf.booking.application.service.RescheduleBookingService;
import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingRequest;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingResponse;
import com.bookly.backendcf.booking.presentation.dto.RescheduleBookingRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservas")
public class BookingController {

    private final CreateBookingService createBookingService;
    private final GetBookingService getBookingService;
    private final CancelBookingService cancelBookingService;
    private final RescheduleBookingService rescheduleBookingService;

    public BookingController(CreateBookingService createBookingService,
                             GetBookingService getBookingService,
                             CancelBookingService cancelBookingService,
                             RescheduleBookingService rescheduleBookingService) {
        this.createBookingService = createBookingService;
        this.getBookingService = getBookingService;
        this.cancelBookingService = cancelBookingService;
        this.rescheduleBookingService = rescheduleBookingService;
    }

    @PostMapping
    public ResponseEntity<?> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            UUID customerId = UUID.fromString(auth.getName());

            Booking booking = createBookingService.createBooking(
                    customerId,
                    request.getProfessionalId(),
                    request.getServiceId(),
                    request.getStartTime(),
                    request.getEndTime()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CreateBookingResponse(
                            booking.getId(),
                            booking.getCustomerId(),
                            booking.getProfessionalId(),
                            booking.getServiceId(),
                            booking.getStartTime(),
                            booking.getEndTime(),
                            booking.getStatus().toString(),
                            booking.getCreatedAt()
                    ));
        } catch (BookingConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(e.getErrorCode(), e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(getBookingService.getBookingById(id));
    }

    @GetMapping("/cliente/{customerId}")
    public ResponseEntity<List<Booking>> getByCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(getBookingService.getByCustomerId(customerId));
    }

    @GetMapping("/profesional/{professionalId}")
    public ResponseEntity<List<Booking>> getByProfessional(@PathVariable UUID professionalId) {
        return ResponseEntity.ok(getBookingService.getByProfessionalId(professionalId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable UUID id) {
        try {
            cancelBookingService.cancelBooking(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/reschedule")
    public ResponseEntity<?> rescheduleBooking(@PathVariable UUID id,
                                               @Valid @RequestBody RescheduleBookingRequest request) {
        try {
            Booking booking = rescheduleBookingService.rescheduleBooking(
                    id, request.startTime(), request.endTime());
            return ResponseEntity.ok(booking);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        }
    }

    private record ErrorResponse(String error, String message) {}
}