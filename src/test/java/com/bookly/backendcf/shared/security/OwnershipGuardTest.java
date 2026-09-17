package com.bookly.backendcf.shared.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OwnershipGuardTest {

    private final OwnershipGuard guard = new OwnershipGuard();

    @Test
    void elDuenoDelRecursoPuedeAccederASuPropioRecurso() {
        UUID ownerId = UUID.randomUUID();
        Authentication authentication = customerAuthentication(ownerId);

        assertDoesNotThrow(() -> guard.check(ownerId, authentication));
    }

    @Test
    void unAdministradorPuedeAccederACualquierRecurso() {
        UUID resourceOwnerId = UUID.randomUUID();
        Authentication authentication = adminAuthentication(UUID.randomUUID());

        assertDoesNotThrow(() -> guard.check(resourceOwnerId, authentication));
    }

    @Test
    void unUsuarioNoPuedeAccederAlRecursoDeOtro() {
        UUID resourceOwnerId = UUID.randomUUID();
        Authentication authentication = customerAuthentication(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> guard.check(resourceOwnerId, authentication));
    }

    private Authentication customerAuthentication(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private Authentication adminAuthentication(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
