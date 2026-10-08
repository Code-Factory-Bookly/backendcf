package com.bookly.backendcf.audit.application;

import com.bookly.backendcf.audit.domain.model.AuditLog;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Puerto de consulta usado por el caso de uso administrativo de auditoría. */
public interface AuditLogQuery {

    Page<AuditLog> findByOccurredAtBetween(OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
