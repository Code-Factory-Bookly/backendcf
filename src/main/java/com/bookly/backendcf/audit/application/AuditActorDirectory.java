package com.bookly.backendcf.audit.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Resuelve quien es cada actor de la bitacora para mostrarlo legible, sin acoplar audit a auth. */
public interface AuditActorDirectory {

    Map<UUID, Actor> findByIds(Collection<UUID> actorIds);

    record Actor(String fullName, String email) {
    }
}
