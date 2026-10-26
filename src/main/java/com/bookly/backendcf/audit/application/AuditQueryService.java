package com.bookly.backendcf.audit.application;

import com.bookly.backendcf.audit.domain.model.AuditLog;
import com.bookly.backendcf.audit.presentation.dto.AuditLogPageResponse;
import com.bookly.backendcf.audit.presentation.dto.AuditLogResponse;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final AuditActorDirectory actorDirectory;

    public AuditQueryService(AuditLogQuery auditLogQuery, AuditActorDirectory actorDirectory) {
        this.auditLogQuery = auditLogQuery;
        this.actorDirectory = actorDirectory;
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse find(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        DateRange range = resolveDateRange(from, to);
        validate(range.from(), range.to(), page, size);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        Page<AuditLog> result = auditLogQuery.findByOccurredAtBetween(
                range.from(), range.to(), pageable);

        Map<UUID, AuditActorDirectory.Actor> actors = actorDirectory.findByIds(
                result.getContent().stream().map(AuditLog::getActorUserId).collect(Collectors.toSet()));

        return new AuditLogPageResponse(
                result.getContent().stream().map(auditLog -> {
                    AuditActorDirectory.Actor actor = actors.get(auditLog.getActorUserId());
                    return actor == null
                            ? AuditLogResponse.from(auditLog, null, null)
                            : AuditLogResponse.from(auditLog, actor.fullName(), actor.email());
                }).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private DateRange resolveDateRange(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime resolvedTo = to == null ? now : to;
        OffsetDateTime resolvedFrom = from == null ? resolvedTo.minusDays(1) : from;
        return new DateRange(resolvedFrom, resolvedTo);
    }

    private void validate(OffsetDateTime from, OffsetDateTime to, int page, int size) {
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

    private record DateRange(OffsetDateTime from, OffsetDateTime to) {
    }
}
