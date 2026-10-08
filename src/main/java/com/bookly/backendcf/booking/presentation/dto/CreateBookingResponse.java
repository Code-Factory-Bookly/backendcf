package com.bookly.backendcf.booking.presentation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class CreateBookingResponse {
    private UUID id;
    private UUID customerId;
    private UUID professionalId;
    private UUID serviceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private LocalDateTime createdAt;

    public CreateBookingResponse(UUID id, UUID customerId, UUID professionalId, UUID serviceId,
                                 LocalDateTime startTime, LocalDateTime endTime, String status, LocalDateTime createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProfessionalId() { return professionalId; }
    public UUID getServiceId() { return serviceId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}