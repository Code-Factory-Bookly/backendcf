package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.booking.application.BookingNameDirectory;
import com.bookly.backendcf.booking.application.service.CreateBookingService;
import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.booking.presentation.dto.BookingResponse;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingRequest;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingResponse;
import com.bookly.backendcf.shared.security.OwnershipGuard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservas")
public class BookingController {

    private final CreateBookingService createBookingService;
    private final BookingRepository bookingRepository;
    private final OwnershipGuard ownershipGuard;
    private final BookingNameDirectory nameDirectory;

    public BookingController(
            CreateBookingService createBookingService,
            BookingRepository bookingRepository,
            OwnershipGuard ownershipGuard,
            BookingNameDirectory nameDirectory) {
        this.createBookingService = createBookingService;
        this.bookingRepository = bookingRepository;
        this.ownershipGuard = ownershipGuard;
        this.nameDirectory = nameDirectory;
    }

    @GetMapping("/cliente/{customerId}")
    public List<BookingResponse> getByCustomer(@PathVariable UUID customerId, Authentication authentication) {
        ownershipGuard.check(customerId, authentication);
        return withNames(bookingRepository.findByCustomerId(customerId));
    }

    @GetMapping("/profesional/{professionalId}")
    public List<BookingResponse> getByProfessional(
            @PathVariable UUID professionalId, Authentication authentication) {
        ownershipGuard.check(professionalId, authentication);
        return withNames(bookingRepository.findByProfessionalId(professionalId));
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

    private List<BookingResponse> withNames(List<Booking> bookings) {
        Map<UUID, String> accountNames = nameDirectory.accountNames(bookings.stream()
                .flatMap(b -> Stream.of(b.getCustomerId(), b.getProfessionalId()))
                .distinct()
                .toList());
        Map<UUID, String> serviceNames = nameDirectory.serviceNames(bookings.stream()
                .map(Booking::getServiceId)
                .distinct()
                .toList());
        return bookings.stream()
                .map(b -> BookingResponse.from(
                        b,
                        accountNames.get(b.getCustomerId()),
                        accountNames.get(b.getProfessionalId()),
                        serviceNames.get(b.getServiceId())))
                .toList();
    }

    private record ErrorResponse(String error, String message) {}
}