package com.bookly.backendcf.schedule.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record AgendaBlockRequest(
        @NotNull(message = "Es obligatorio") LocalDateTime startTime,
        @NotNull(message = "Es obligatorio") LocalDateTime endTime,
        @Size(max = 200, message = "Máximo 200 caracteres") String reason) {
}
