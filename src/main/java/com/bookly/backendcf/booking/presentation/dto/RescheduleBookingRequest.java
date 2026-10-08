package com.bookly.backendcf.booking.presentation.dto;

import jakarta.validation.constraints.Future;
import java.time.LocalDateTime;

public record RescheduleBookingRequest(
        @Future(message = "startTime debe ser en el futuro")
        LocalDateTime startTime,

        @Future(message = "endTime debe ser en el futuro")
        LocalDateTime endTime
) {}