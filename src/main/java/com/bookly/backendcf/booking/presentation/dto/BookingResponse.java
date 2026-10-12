package com.bookly.backendcf.booking.presentation.dto;

import com.bookly.backendcf.booking.domain.model.Booking;
import java.time.LocalDateTime;
import java.util.UUID;

public class BookingResponse {
    private final UUID id;
    private final UUID customerId;
    private final UUID professionalId;
    private final UUID serviceId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime cancelledAt;
    private final String cancellationReason;

    public BookingResponse(Booking booking) {
        this.id = booking.getId();
        this.customerId = booking.getCustomerId();
        this.professionalId = booking.getProfessionalId();
        this.serviceId = booking.getServiceId();
        this.startTime = booking.getStartTime();
        this.endTime = booking.getEndTime();
        this.status = booking.getStatus().name();
        this.createdAt = booking.getCreatedAt();
        this.updatedAt = booking.getUpdatedAt();
        this.cancelledAt = booking.getCancelledAt();
        this.cancellationReason = booking.getCancellationReason();
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProfessionalId() { return professionalId; }
    public UUID getServiceId() { return serviceId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
}
