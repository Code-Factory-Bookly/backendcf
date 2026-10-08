package com.bookly.backendcf.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextActorIdentityProviderTest {

    private final SecurityContextActorIdentityProvider provider =
            new SecurityContextActorIdentityProvider();

    @AfterEach
    void limpiaElContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void devuelveElUuidDelActorAutenticado() {
        UUID actorId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(actorId.toString());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(provider.currentActorId()).isEqualTo(actorId);
    }

    @Test
    void rechazaLaAusenciaDeAutenticacion() {
        assertThatThrownBy(provider::currentActorId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No hay un actor autenticado para la operacion");
    }

    @Test
    void rechazaUnaAutenticacionNoValidada() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(provider::currentActorId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No hay un actor autenticado para la operacion");
    }

    @Test
    void rechazaUnaIdentidadQueNoContieneUnUuid() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("identidad-invalida");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(provider::currentActorId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La identidad autenticada no contiene un UUID valido")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
