package com.bookly.backendcf.booking.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.bookly.backendcf.audit.application.AuditRecorder;
import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BookingCreatedAuditListenerTest {

    @Test
    void adaptaLaCreacionDeReservaAlContratoDeAuditoria() {
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        BookingCreatedAuditListener listener = new BookingCreatedAuditListener(auditRecorder);
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 10, 10, 9, 0);
        LocalDateTime end = start.plusHours(1);

        BookingCreatedEvent event = new BookingCreatedEvent(
                bookingId, customerId, professionalId, serviceId, start, end);

        listener.onBookingCreated(event);

        ArgumentCaptor<AuditAction> captor = ArgumentCaptor.forClass(AuditAction.class);
        verify(auditRecorder).record(captor.capture());
        AuditAction action = captor.getValue();

        assertThat(action.actorUserId()).isEqualTo(customerId);
        assertThat(action.actionType()).isEqualTo(AuditActionType.BOOKING_CREATED);
        assertThat(action.resourceType()).isEqualTo(AuditResourceType.BOOKING);
        assertThat(action.resourceId()).isEqualTo(bookingId);
        assertThat(action.occurredAt()).isEqualTo(event.getOccurredAt().atOffset(ZoneOffset.UTC));
        assertThat(action.sourceIp()).isNull();
        assertThat(action.metadata())
                .containsEntry("professionalId", professionalId.toString())
                .containsEntry("serviceId", serviceId.toString())
                .containsEntry("startTime", start.toString())
                .containsEntry("endTime", end.toString());
    }
}
