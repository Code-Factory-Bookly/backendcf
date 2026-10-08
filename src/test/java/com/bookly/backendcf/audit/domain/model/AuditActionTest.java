package com.bookly.backendcf.audit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditActionTest {

    @Test
    void conservaLosDatosDelContratoYHaceInmutableElMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("status", "CONFIRMED");

        AuditAction action = new AuditAction(
                UUID.randomUUID(),
                AuditActionType.BOOKING_CREATED,
                AuditResourceType.BOOKING,
                UUID.randomUUID(),
                OffsetDateTime.now(),
                "127.0.0.1",
                metadata);

        metadata.put("status", "CANCELLED");

        assertThat(action.metadata()).containsEntry("status", "CONFIRMED");
        assertThatThrownBy(() -> action.metadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void permiteMetadataAusenteComoMapaVacio() {
        AuditAction action = new AuditAction(
                UUID.randomUUID(),
                AuditActionType.ROLE_CHANGED,
                AuditResourceType.USER,
                UUID.randomUUID(),
                OffsetDateTime.now(),
                null,
                null);

        assertThat(action.metadata()).isEmpty();
    }

    @Test
    void exigeActorAccionRecursoYFecha() {
        UUID resourceId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now();

        assertThatThrownBy(() -> new AuditAction(null, AuditActionType.ROLE_CHANGED,
                AuditResourceType.USER, resourceId, occurredAt, null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("actorUserId es obligatorio");
        assertThatThrownBy(() -> new AuditAction(UUID.randomUUID(), null,
                AuditResourceType.USER, resourceId, occurredAt, null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("actionType es obligatorio");
        assertThatThrownBy(() -> new AuditAction(UUID.randomUUID(), AuditActionType.ROLE_CHANGED,
                null, resourceId, occurredAt, null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("resourceType es obligatorio");
        assertThatThrownBy(() -> new AuditAction(UUID.randomUUID(), AuditActionType.ROLE_CHANGED,
                AuditResourceType.USER, resourceId, null, null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("occurredAt es obligatorio");
    }
}
