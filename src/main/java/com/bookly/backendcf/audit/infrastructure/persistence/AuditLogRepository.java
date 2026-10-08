package com.bookly.backendcf.audit.infrastructure.persistence;

import com.bookly.backendcf.audit.domain.model.AuditLog;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistencia de auditoría. No expone métodos de actualización o eliminación. */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByOccurredAtBetweenOrderByOccurredAtDescIdDesc(
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
