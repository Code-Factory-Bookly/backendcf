package com.bookly.backendcf.audit.application;

import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditLog;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates and persists a critical action through the audit application port. */
@Service
public class RegisterAuditActionService implements AuditRecorder {

    private final AuditLogStore auditLogStore;

    public RegisterAuditActionService(AuditLogStore auditLogStore) {
        this.auditLogStore = auditLogStore;
    }

    @Override
    @Transactional
    public void record(AuditAction action) {
        Objects.requireNonNull(action, "action es obligatoria");
        validateResource(action);
        auditLogStore.saveAndFlush(AuditLog.from(action));
    }

    private void validateResource(AuditAction action) {
        if (action.resourceId() == null) {
            throw new IllegalArgumentException("resourceId es obligatorio para una accion auditable");
        }

        AuditResourceType expectedResource = switch (action.actionType()) {
            case BOOKING_CREATED, BOOKING_CANCELLED -> AuditResourceType.BOOKING;
            case ROLE_CHANGED -> AuditResourceType.USER;
        };

        if (action.resourceType() != expectedResource) {
            throw new IllegalArgumentException(
                    "resourceType no corresponde a " + action.actionType());
        }
    }
}
