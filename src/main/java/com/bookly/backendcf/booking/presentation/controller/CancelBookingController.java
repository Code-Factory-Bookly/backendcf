
package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.booking.application.service.CancelBookingService;
import com.bookly.backendcf.booking.domain.model.Booking;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservas")
public class CancelBookingController {

    private final CancelBookingService cancelBookingService;

    public CancelBookingController(CancelBookingService cancelBookingService) {
        this.cancelBookingService = cancelBookingService;
    }

    @PatchMapping("/{bookingId}/cancelacion")
    public ResponseEntity<CancelBookingResponse> cancelBooking(
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) CancelBookingRequest request
    ) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String reason = request == null ? null : request.reason();

        Booking booking = cancelBookingService.cancelBooking(
                bookingId,
                authentication,
                reason
        );

        return ResponseEntity.ok(new CancelBookingResponse(
                booking.getId(),
                booking.getStatus().name(),
                booking.getCancelledAt(),
                booking.getCancellationReason()
        ));
    }

    public record CancelBookingRequest(
            @Size(max = 500) String reason
    ) {}

    public record CancelBookingResponse(
            UUID bookingId,
            String status,
            LocalDateTime cancelledAt,
            String cancellationReason
    ) {}
}
