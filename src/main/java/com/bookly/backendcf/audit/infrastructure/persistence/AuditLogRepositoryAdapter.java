package com.bookly.backendcf.audit.infrastructure.persistence;

import com.bookly.backendcf.audit.application.AuditLogStore;
import com.bookly.backendcf.audit.application.AuditLogQuery;
import com.bookly.backendcf.audit.domain.model.AuditLog;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Adaptador que conecta el puerto de persistencia con Spring Data JPA. */
@Component
public class AuditLogRepositoryAdapter implements AuditLogStore, AuditLogQuery {

    private final AuditLogRepository repository;

    public AuditLogRepositoryAdapter(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditLog saveAndFlush(AuditLog auditLog) {
        return repository.saveAndFlush(auditLog);
    }

    @Override
    public Page<AuditLog> findByOccurredAtBetween(OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        return repository.findByOccurredAtBetweenOrderByOccurredAtDescIdDesc(from, to, pageable);
    }
}
