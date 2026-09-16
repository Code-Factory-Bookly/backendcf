package com.bookly.backendcf.auth.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserAccountTest {

    private UserAccount account;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        account = new UserAccount("patient@example.com", "hashed-password", "Patient One");
        now = OffsetDateTime.now();
    }

    @Test
    void unaCuentaNuevaQuedaHabilitadaConRolPacienteYSinIntentosFallidos() {
        assertEquals(UserRole.PATIENT, account.getRole());
        assertTrue(account.isEnabled());
        assertEquals(0, account.getFailedLoginAttempts());
        assertNull(account.getLockedUntil());
        assertEquals(account.getCreatedAt(), account.getUpdatedAt());
    }

    @Test
    void unaCuentaSinBloqueoNoEstaBloqueada() {
        assertFalse(account.isLocked(now));
    }

    @Test
    void unaCuentaConBloqueoYaVencidoNoEstaBloqueada() {
        account.registerFailedLogin(now.minusMinutes(30), 1, 15);

        assertFalse(account.isLocked(now));
    }

    @Test
    void unIntentoFallidoPorDebajoDelLimiteNoBloqueaLaCuenta() {
        account.registerFailedLogin(now, 5, 15);

        assertEquals(1, account.getFailedLoginAttempts());
        assertNull(account.getLockedUntil());
        assertFalse(account.isLocked(now));
    }

    @Test
    void alcanzarElLimiteDeIntentosBloqueaLaCuentaPorElTiempoConfigurado() {
        for (int i = 0; i < 4; i++) {
            account.registerFailedLogin(now, 5, 15);
        }
        account.registerFailedLogin(now, 5, 15);

        assertEquals(5, account.getFailedLoginAttempts());
        assertTrue(account.isLocked(now));
        assertEquals(now.plusMinutes(15), account.getLockedUntil());
    }

    @Test
    void unLoginExitosoReiniciaElContadorYQuitaElBloqueo() {
        account.registerFailedLogin(now, 1, 15);
        assertTrue(account.isLocked(now));

        account.registerSuccessfulLogin(now.plusMinutes(1));

        assertEquals(0, account.getFailedLoginAttempts());
        assertNull(account.getLockedUntil());
        assertFalse(account.isLocked(now.plusMinutes(1)));
    }
}
