package com.bookly.backendcf.audit.presentation.dto;

import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditLog;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorUserId,
        AuditActionType actionType,
        AuditResourceType resourceType,
        UUID resourceId,
        OffsetDateTime occurredAt,
        String sourceIp,
        Map<String, String> metadata) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUserId(),
                auditLog.getActionType(),
                auditLog.getResourceType(),
                auditLog.getResourceId(),
                auditLog.getOccurredAt(),
                auditLog.getSourceIp(),
                auditLog.getMetadata());
    }
}
