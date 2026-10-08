package com.bookly.backendcf.audit.application;

import com.bookly.backendcf.audit.domain.model.AuditLog;
import com.bookly.backendcf.audit.presentation.dto.AuditLogPageResponse;
import com.bookly.backendcf.audit.presentation.dto.AuditLogResponse;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrative read use case for the append-only audit log. */
@Service
public class AuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogQuery auditLogQuery;

    public AuditQueryService(AuditLogQuery auditLogQuery) {
        this.auditLogQuery = auditLogQuery;
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse find(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        validate(from, to, page, size);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        Page<AuditLog> result = auditLogQuery.findByOccurredAtBetween(from, to, pageable);

        return new AuditLogPageResponse(
                result.getContent().stream().map(AuditLogResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private void validate(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        if (from == null || to == null) {
            throw new InvalidAuditQueryException(
                    "El rango de fechas es obligatorio",
                    Map.of("from", "Es obligatorio", "to", "Es obligatorio"));
        }
        if (from.isAfter(to)) {
            throw new InvalidAuditQueryException(
                    "La fecha inicial no puede ser posterior a la fecha final",
                    Map.of("from", "Debe ser menor o igual que to"));
        }
        if (page < 0) {
            throw new InvalidAuditQueryException(
                    "La página no puede ser negativa",
                    Map.of("page", "Debe ser mayor o igual que 0"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidAuditQueryException(
                    "El tamaño de página no es válido",
                    Map.of("size", "Debe estar entre 1 y " + MAX_PAGE_SIZE));
        }
    }
}
