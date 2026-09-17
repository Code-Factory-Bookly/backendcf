package com.bookly.backendcf.shared.security;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Mecanismo de autorización por propiedad de recurso (HU-21), independiente de cualquier
 * entidad de negocio concreta. Cuando exista un recurso con dueño (ej. Reserva, HU-08), su
 * controlador/servicio solo necesita llamar a {@link #check(UUID, Authentication)} con el
 * id del dueño del recurso.
 */
@Component
public class OwnershipGuard {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");

    public void check(UUID resourceOwnerId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }

        UUID currentUserId = UUID.fromString(authentication.getName());
        if (!currentUserId.equals(resourceOwnerId)) {
            logAccessDenied(currentUserId, resourceOwnerId);
            throw new AccessDeniedException("No tiene permisos para este recurso");
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private void logAccessDenied(UUID currentUserId, UUID resourceOwnerId) {
        SECURITY_LOG.warn(
                "{\"event\":\"ACCESS_DENIED\",\"userId\":\"{}\",\"resourceOwnerId\":\"{}\",\"timestamp\":\"{}\"}",
                currentUserId, resourceOwnerId, OffsetDateTime.now());
    }
}
