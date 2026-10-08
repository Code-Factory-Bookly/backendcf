package com.bookly.backendcf.audit.infrastructure.persistence;

import com.bookly.backendcf.audit.domain.model.AuditLog;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Persistencia de auditoría. No expone métodos de actualización o eliminación. */
public interface AuditLogRepository extends Repository<AuditLog, UUID> {

    AuditLog saveAndFlush(AuditLog auditLog);

    Page<AuditLog> findByOccurredAtBetweenOrderByOccurredAtDescIdDesc(
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
