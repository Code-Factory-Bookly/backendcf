package com.bookly.backendcf.shared.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the actor from the already validated authentication context. */
@Component
public class SecurityContextActorIdentityProvider implements ActorIdentityProvider {

    @Override
    public UUID currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No hay un actor autenticado para la operacion");
        }

        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("La identidad autenticada no contiene un UUID valido", exception);
        }
    }
}
