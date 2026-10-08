package com.bookly.backendcf.booking.infrastructure.event;

import com.bookly.backendcf.audit.application.AuditRecorder;
import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Adapts the booking event contract to the audit application port. */
@Component
public class BookingCreatedAuditListener {

    private final AuditRecorder auditRecorder;

    public BookingCreatedAuditListener(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @EventListener
    public void onBookingCreated(BookingCreatedEvent event) {
        auditRecorder.record(new AuditAction(
                event.getCustomerId(),
                AuditActionType.BOOKING_CREATED,
                AuditResourceType.BOOKING,
                event.getBookingId(),
                event.getOccurredAt().atOffset(ZoneOffset.UTC),
                null,
                Map.of(
                        "professionalId", event.getProfessionalId().toString(),
                        "serviceId", event.getServiceId().toString(),
                        "startTime", event.getStartTime().toString(),
                        "endTime", event.getEndTime().toString())));
    }
}
