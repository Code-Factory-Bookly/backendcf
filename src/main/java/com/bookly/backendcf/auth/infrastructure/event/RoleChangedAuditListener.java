package com.bookly.backendcf.auth.infrastructure.event;

import com.bookly.backendcf.audit.application.AuditRecorder;
import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import com.bookly.backendcf.auth.domain.events.RoleChangedEvent;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Adapts role changes to the audit application port. */
@Component
public class RoleChangedAuditListener {

    private final AuditRecorder auditRecorder;

    public RoleChangedAuditListener(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @EventListener
    public void onRoleChanged(RoleChangedEvent event) {
        auditRecorder.record(new AuditAction(
                event.actorUserId(),
                AuditActionType.ROLE_CHANGED,
                AuditResourceType.USER,
                event.targetUserId(),
                event.occurredAt(),
                null,
                Map.of(
                        "previousRole", event.previousRole().name(),
                        "newRole", event.newRole().name())));
    }
}
