package com.bookly.backendcf.audit.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Contrato inmutable que transporta la evidencia mínima de una acción crítica.
 *
 * <p>No contiene credenciales, tokens ni datos personales innecesarios. {@code resourceId} y
 * {@code sourceIp} pueden ser nulos porque el contrato también debe soportar acciones o contextos
 * donde esos datos no estén disponibles; el actor, la acción, el recurso y la fecha sí son
 * obligatorios.
 */
public record AuditAction(
        UUID actorUserId,
        AuditActionType actionType,
        AuditResourceType resourceType,
        UUID resourceId,
        OffsetDateTime occurredAt,
        String sourceIp,
        Map<String, String> metadata) {

    public AuditAction {
        Objects.requireNonNull(actorUserId, "actorUserId es obligatorio");
        Objects.requireNonNull(actionType, "actionType es obligatorio");
        Objects.requireNonNull(resourceType, "resourceType es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
