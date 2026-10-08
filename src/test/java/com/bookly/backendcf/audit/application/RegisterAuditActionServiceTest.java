package com.bookly.backendcf.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditLog;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegisterAuditActionServiceTest {

    private RecordingAuditLogStore repository;
    private RegisterAuditActionService service;

    @BeforeEach
    void setUp() {
        repository = new RecordingAuditLogStore();
        service = new RegisterAuditActionService(repository);
    }

    @Test
    void registraUnaAccionDeReservaValida() {
        AuditAction action = action(AuditActionType.BOOKING_CREATED, AuditResourceType.BOOKING);

        service.record(action);

        assertThat(repository.saved).isNotNull();
    }

    @Test
    void rechazaUnaAccionSinRecurso() {
        AuditAction action = new AuditAction(
                UUID.randomUUID(),
                AuditActionType.ROLE_CHANGED,
                AuditResourceType.USER,
                null,
                OffsetDateTime.now(),
                null,
                Map.of());

        assertThatThrownBy(() -> service.record(action))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("resourceId es obligatorio para una accion auditable");
        assertThat(repository.saved).isNull();
    }

    @Test
    void rechazaRecursoIncompatibleConLaAccion() {
        AuditAction action = action(AuditActionType.ROLE_CHANGED, AuditResourceType.BOOKING);

        assertThatThrownBy(() -> service.record(action))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("resourceType no corresponde a ROLE_CHANGED");
        assertThat(repository.saved).isNull();
    }

    private AuditAction action(AuditActionType actionType, AuditResourceType resourceType) {
        return new AuditAction(
                UUID.randomUUID(),
                actionType,
                resourceType,
                UUID.randomUUID(),
                OffsetDateTime.now(),
                "127.0.0.1",
                Map.of("status", "CONFIRMED"));
    }

    private static final class RecordingAuditLogStore implements AuditLogStore {

        private AuditLog saved;

        @Override
        public AuditLog saveAndFlush(AuditLog auditLog) {
            saved = auditLog;
            return auditLog;
        }
    }
}
