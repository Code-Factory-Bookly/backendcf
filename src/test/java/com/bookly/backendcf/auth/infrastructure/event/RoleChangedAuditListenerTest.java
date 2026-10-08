package com.bookly.backendcf.auth.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.bookly.backendcf.audit.application.AuditRecorder;
import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import com.bookly.backendcf.auth.domain.events.RoleChangedEvent;
import com.bookly.backendcf.auth.domain.model.UserRole;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleChangedAuditListenerTest {

    @Test
    void adaptaElCambioDeRolAlContratoDeAuditoria() {
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        RoleChangedAuditListener listener = new RoleChangedAuditListener(auditRecorder);
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-10-08T12:00:00Z");

        listener.onRoleChanged(new RoleChangedEvent(
                actorId, targetId, UserRole.CUSTOMER, UserRole.PROFESSIONAL, occurredAt));

        ArgumentCaptor<AuditAction> captor = ArgumentCaptor.forClass(AuditAction.class);
        verify(auditRecorder).record(captor.capture());
        AuditAction action = captor.getValue();

        assertThat(action.actorUserId()).isEqualTo(actorId);
        assertThat(action.actionType()).isEqualTo(AuditActionType.ROLE_CHANGED);
        assertThat(action.resourceType()).isEqualTo(AuditResourceType.USER);
        assertThat(action.resourceId()).isEqualTo(targetId);
        assertThat(action.occurredAt()).isEqualTo(occurredAt);
        assertThat(action.metadata())
                .containsEntry("previousRole", "CUSTOMER")
                .containsEntry("newRole", "PROFESSIONAL");
    }
}
