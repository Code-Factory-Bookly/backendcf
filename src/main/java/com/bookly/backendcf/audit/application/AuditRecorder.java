package com.bookly.backendcf.audit.application;

import com.bookly.backendcf.audit.domain.model.AuditAction;

/**
 * Puerto de aplicación para registrar una acción crítica.
 *
 * <p>Los módulos consumidores dependen de este contrato y no de la persistencia concreta de
 * auditoría. La implementación será responsable de guardar la acción de forma append-only.
 */
public interface AuditRecorder {

    void record(AuditAction action);
}
