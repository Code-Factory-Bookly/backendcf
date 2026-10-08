package com.bookly.backendcf.booking.presentation.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

public class CreateBookingRequest {
    @NotNull(message = "professionalId no puede ser nulo")
    private UUID professionalId;

    @NotNull(message = "serviceId no puede ser nulo")
    private UUID serviceId;

    @NotNull(message = "startTime no puede ser nulo")
    @Future(message = "startTime debe ser en el futuro")
    private LocalDateTime startTime;

    @NotNull(message = "endTime no puede ser nulo")
    @Future(message = "endTime debe ser en el futuro")
    private LocalDateTime endTime;

    public CreateBookingRequest() {}

    public CreateBookingRequest(UUID professionalId, UUID serviceId,
                                LocalDateTime startTime, LocalDateTime endTime) {
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getProfessionalId() { return professionalId; }
    public UUID getServiceId() { return serviceId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }

    public void setProfessionalId(UUID professionalId) { this.professionalId = professionalId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}