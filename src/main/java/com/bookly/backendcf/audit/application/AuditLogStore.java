package com.bookly.backendcf.audit.application;

import com.bookly.backendcf.audit.domain.model.AuditLog;

/** Puerto de persistencia usado por el caso de uso de auditoría. */
public interface AuditLogStore {

    AuditLog saveAndFlush(AuditLog auditLog);
}
