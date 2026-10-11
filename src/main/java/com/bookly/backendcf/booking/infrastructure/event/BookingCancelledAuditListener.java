
package com.bookly.backendcf.booking.infrastructure.event;

import com.bookly.backendcf.audit.application.AuditRecorder;
import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import com.bookly.backendcf.booking.domain.events.BookingCancelledEvent;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 *HU-09 / HU-19:
 *registra la cancelacion de una reserva en la auditoria :)
 *
 *el evento debe publicarse dentro de la transaccion
 *de cancelacion para que ambos cambios sean atomicos
 */
@Component
public class BookingCancelledAuditListener {

    private final AuditRecorder auditRecorder;

    public BookingCancelledAuditListener(
            AuditRecorder auditRecorder
    ) {
        this.auditRecorder = auditRecorder;
    }

    @EventListener
    public void onBookingCancelled(BookingCancelledEvent event) {

        Map<String, String> metadata = new HashMap<>();

        metadata.put(
                "professionalId",
                event.getProfessionalId().toString()
        );

        metadata.put(
                "serviceId",
                event.getServiceId().toString()
        );

        metadata.put(
                "startTime",
                event.getStartTime().toString()
        );

        metadata.put(
                "endTime",
                event.getEndTime().toString()
        );

        metadata.put(
                "cancelledAt",
                event.getCancelledAt().toString()
        );

        if (event.getCancellationReason() != null) {
            metadata.put(
                    "cancellationReason",
                    event.getCancellationReason()
            );
        }

        auditRecorder.record(
                new AuditAction(
                        event.getCustomerId(),
                        AuditActionType.BOOKING_CANCELLED,
                        AuditResourceType.BOOKING,
                        event.getBookingId(),
                        event.getOccurredAt()
                                .atOffset(ZoneOffset.UTC),
                        null,
                        Map.copyOf(metadata)
                )
        );
    }
}
